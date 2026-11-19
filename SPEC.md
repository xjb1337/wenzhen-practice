# AI ER Virtual Patient Simulator - Technical Spec

## Architecture Overview
- **Frontend:** Next.js App Router + Web Audio API (Push-to-talk clinical interview) + Dynamic Vitals Monitor (`HR`, `BP`, `SpO2`).
- **Backend / Dual-Agent System:**
  1. **Patient Agent:** Grounded in clinical case templates + streaming TTS (pain/distress inflection).
  2. **Evaluator Agent:** Grades diagnostic reasoning, red-flag detection (e.g., mesenteric ischemia vs. appendicitis), and bedside manner against standard rubrics.

## Devin Execution Plan
- **Session 1:** ER UI & dynamic vitals state machine (`patient-state.schema.json`).
- **Session 2:** Whisper STT & streaming TTS audio pipeline.
- **Session 3:** Dual-agent orchestration + Vitest/Playwright test gates.
