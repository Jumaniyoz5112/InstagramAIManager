from __future__ import annotations

import logging

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response

from app.config import OLLAMA_TEXT_MODEL, OLLAMA_VISION_MODEL
from app.services.speech import DEFAULT_VOICE, VOICES
from app.schemas import AnalyzeRequest, InsightsRequest, ScenarioRequest, SpeechRequest
from app.services.analyze import run_analyze
from app.services.groq import groq_configured
from app.services.insights import run_insights
from app.services.ollama import ollama_busy, ollama_reachable
from app.services.scenario import run_scenario, scenario_model_name
from app.services.speech import synthesize

logging.basicConfig(level=logging.INFO)

app = FastAPI(title="InstagramAIManager AI Service", version="0.4.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:4200", "http://localhost:8080"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health():
    ollama_ok = ollama_reachable()
    busy = ollama_busy()
    return {
        "status": "UP" if ollama_ok else "DEGRADED",
        "service": "ai-service",
        "ollama": ollama_ok,
        "sequential": True,
        "busy": busy.get("busy"),
        "busyModel": busy.get("model"),
        "visionModel": OLLAMA_VISION_MODEL,
        "textModel": OLLAMA_TEXT_MODEL,
        "scenarioProvider": "groq" if groq_configured() else "ollama",
        "scenarioModel": scenario_model_name(),
        "model": OLLAMA_VISION_MODEL,
    }


@app.get("/")
def root():
    return {
        "message": "InstagramAIManager AI Service",
        "visionModel": OLLAMA_VISION_MODEL,
        "textModel": OLLAMA_TEXT_MODEL,
        "scenarioProvider": "groq" if groq_configured() else "ollama",
        "scenarioModel": scenario_model_name(),
    }


@app.post("/analyze")
def analyze(req: AnalyzeRequest):
    return run_analyze(req)


@app.post("/insights")
def insights(req: InsightsRequest):
    return run_insights(req)


@app.post("/scenario")
def scenario(req: ScenarioRequest):
    return run_scenario(req)


@app.get("/speech/voices")
def speech_voices():
    return {"voices": [{"id": voice_id, "language": "uz"} for voice_id in VOICES]}


@app.post("/speech")
def speech(req: SpeechRequest):
    try:
        audio = synthesize(req.text, req.voice or DEFAULT_VOICE)
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex
    except Exception as ex:
        logging.getLogger("ai-service.speech").exception("speech failed")
        raise HTTPException(status_code=502, detail="speech failed") from ex
    return Response(content=audio, media_type="audio/wav")
