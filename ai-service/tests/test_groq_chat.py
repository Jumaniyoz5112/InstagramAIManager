from __future__ import annotations

import unittest
from unittest.mock import MagicMock, patch

from fastapi import HTTPException

from app.schemas import ScenarioRequest
from app.services.groq import groq_json_chat
from app.services.quality import looks_like_gibberish_uz
from app.services.scenario import run_scenario


class GroqJsonChatTest(unittest.TestCase):
    def test_parses_json_content_and_hides_reasoning(self) -> None:
        response = MagicMock()
        response.status_code = 200
        response.json.return_value = {
            "choices": [{"message": {"content": '{"titleUz":"Nonushta story"}'}}]
        }
        response.raise_for_status.return_value = None
        client = MagicMock()
        client.__enter__.return_value = client
        client.post.return_value = response

        with patch("app.services.groq.config.GROQ_API_KEY", "test-key"), patch(
            "app.services.groq.config.GROQ_TEXT_MODEL", "qwen/qwen3.8-27b"
        ), patch("app.services.groq.config.GROQ_BASE_URL", "https://api.groq.com/openai/v1"), patch(
            "app.services.groq.httpx.Client", return_value=client
        ):
            parsed = groq_json_chat(system="json", user_text="brief", temperature=0.7)

        self.assertEqual(parsed["titleUz"], "Nonushta story")
        payload = client.post.call_args.kwargs["json"]
        self.assertEqual(payload["model"], "qwen/qwen3.8-27b")
        self.assertEqual(payload["reasoning_effort"], "none")
        self.assertEqual(payload["response_format"], {"type": "json_object"})
        self.assertEqual(client.post.call_args.kwargs["headers"]["Authorization"], "Bearer test-key")

    def test_rate_limit_is_429(self) -> None:
        response = MagicMock()
        response.status_code = 429
        client = MagicMock()
        client.__enter__.return_value = client
        client.post.return_value = response

        with patch("app.services.groq.config.GROQ_API_KEY", "test-key"), patch(
            "app.services.groq.httpx.Client", return_value=client
        ):
            with self.assertRaises(HTTPException) as raised:
                groq_json_chat(system="json", user_text="brief", temperature=0.7)

        self.assertEqual(raised.exception.status_code, 429)

    def test_caption_of_two_sentences_is_not_gibberish(self) -> None:
        caption = " ".join(["va", "uchun", "shu"] * 7)
        self.assertGreater(len(caption.split()), 18)
        self.assertFalse(looks_like_gibberish_uz(caption, max_tokens=45))
        self.assertTrue(looks_like_gibberish_uz(caption))

    def test_lexicon_noise_does_not_spend_a_second_call(self) -> None:
        draft = {
            "titleUz": "Nonushta tartibi",
            "captionPrimaryUz": "blorfy qantix melvun zorpix daxlen vimpor keltun",
            "hookUz": "Diqqat",
            "ctaUz": "Yozing",
            "format": "STORY",
            "shots": [
                {
                    "visualUz": "yaqin plan",
                    "overlayTextUz": "blorfy qantix",
                    "purpose": "hook",
                },
                {"visualUz": "stol", "overlayTextUz": "non", "purpose": "setup"},
                {"visualUz": "choynak", "overlayTextUz": "choy", "purpose": "detail"},
                {"visualUz": "tuxum", "overlayTextUz": "tuxum", "purpose": "payoff"},
                {"visualUz": "qo'l", "overlayTextUz": "boshladik", "purpose": "cta"},
                {"visualUz": "xona", "overlayTextUz": "tayyor", "purpose": "cta"},
            ],
        }
        chat = MagicMock(return_value=draft)
        insights = {
            "executiveSummaryUz": "Qisqa hook va yaqin plan yaxshi ishlaydi, uzun matn sust.",
            "winningPatterns": ["yaqin plan"],
        }
        with patch("app.services.scenario._scenario_chat", chat):
            out = run_scenario(ScenarioRequest(brief=None, insights=insights, preferredFormat="STORY"))

        self.assertEqual(chat.call_count, 1)
        self.assertEqual(out["schemaVersion"], "1.5")
        self.assertGreaterEqual(len(out["result"]["shots"]), 4)


if __name__ == "__main__":
    unittest.main()
