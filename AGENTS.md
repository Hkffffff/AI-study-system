# AGENTS.md

## 1. Project Overview

This project is a personal AI-powered learning management system.

It is a single-user application for the owner's personal use. There is no registration or login in the MVP.

Detailed product and architecture design lives in `docs/` (start with `docs/README.md`). Keep it in sync with this file.

The primary use case is postgraduate entrance exam preparation, initially covering:

- Mathematics I
- 408 Computer Science Fundamentals

The system is not merely an AI chatbot or a traditional mistake-book application.

Its core concepts are:

1. Archive - preserve original learning materials.
2. Questions - structure individual mistakes extracted from materials.
3. AI - analyze questions, explain concepts, and interact with the user.
4. Learning Space - isolate different subjects while keeping them inside one application.
5. Learning Profile - accumulate long-term information about weaknesses and recurring mistakes.

The system must be designed so that additional learning spaces can be added later without changing the core architecture.

Examples:
- Mathematics
- 408
- Java
- Database
- AI
- English

Do not hard-code Mathematics or 408 as special cases.

---

## 2. Product Principles

### 2.1 One application, multiple learning spaces

Do NOT build separate applications for Mathematics and 408.

The application must support multiple LearningSpaces.

Each LearningSpace may have its own:

- subjects
- archives
- questions
- AI context
- knowledge points
- analysis rules

Mathematics and 408 must be logically isolated but share the same application infrastructure.

### 2.1.1 Subject (lightweight, optional)

A LearningSpace may contain optional Subjects, for example:

- Mathematics I → Calculus, Linear Algebra, Probability and Statistics
- 408 → Data Structures, Computer Organization, Operating Systems, Computer Networks

Rules:

- Subject is an optional grouping inside one LearningSpace, not a new isolation boundary.
- All core data (archives, files, questions, knowledge points, chat sessions) is still owned by `space_id`.
- Archives, Questions and KnowledgePoints may optionally reference one Subject of the same LearningSpace. Cross-space references must be rejected.
- Subjects are flat (no sub-subjects). Finer granularity belongs to KnowledgePoints.
- A Subject may carry optional additional AI instructions.
- A LearningSpace without Subjects must work without any limitation.

---

### 2.2 Archive is the source of truth

Uploaded files and images are original user materials.

AI-generated information must never replace or destroy the original material.

Every AI-generated analysis should be traceable to its source question and/or source file.

Users must be able to view the original uploaded material at any time.

---

### 2.3 AI is an interactive learning assistant

AI is not only an "Analyze" button.

Users should be able to interact with AI:

- globally
- within a LearningSpace
- within an Archive
- within a Question
- with selected files/questions

AI context must respect the current scope.

Do not automatically send the user's entire knowledge base to every AI request.

---

### 2.4 AI must not pretend certainty

When the uploaded image, handwriting, solution process, or context is ambiguous, the AI should explicitly state uncertainty.

Do not fabricate unreadable mathematical formulas or infer unsupported reasoning.

The system should allow the user to correct AI-generated analysis.

### 2.5 AI results require user confirmation

Every successful AI analysis starts as an unreviewed draft.

The user may:

- confirm it
- correct it (creates a new user-edited version, which is confirmed)
- reject it (and re-analyze with more information or another model)

Rules:

- Unconfirmed results must be clearly labeled as AI drafts in the UI.
- Each question has at most one current confirmed analysis version.
- Only confirmed data may feed future review scheduling and the learning profile.
- New knowledge points proposed by AI are created only after the user accepts them.

### 2.6 Confidence

The model's self-reported confidence is not calibrated and must not be used alone.

The system derives a confidence level (HIGH / MEDIUM / LOW) by combining the model confidence with objective signals, such as unreadable content, missing question text, missing user work, output repair, truncated output, or an undetermined error type.

Store the signals that were triggered so the UI can explain the level.

Confidence only affects presentation and confirmation requirements. It never auto-confirms or auto-rejects a result.

---

## 3. Initial AI Question Analysis

The first version of AI question analysis should focus on four core outputs:

1. Core knowledge points
2. Error cause analysis
3. Correct solving approach
4. Related knowledge / easily confused concepts

Optional metadata may include:

- error type
- difficulty
- confidence
- review suggestions

Do not add excessive AI-generated fields without a clear product reason.

---

## 4. AI Input

AI analysis may receive:

- question image
- handwritten solution image
- typed question
- user's answer
- official answer
- related archive files
- user-provided context

The system should support multimodal inputs.

The AI should distinguish between:

- question content
- user's work
- official solution
- annotations
- teacher corrections

Do not assume every visible mark in an image belongs to the question.

---

## 5. AI Output

Prefer structured outputs over unstructured text whenever the output needs to be stored or queried.

Question analysis output has two parts:

1. recognition: question text, user's work, official solution, annotations/teacher corrections, and unreadable parts (with source index and location)
2. analysis: the four core outputs in section 3 plus optional metadata

For example:

{
  "recognition": {
    "questionText": "",
    "userWork": null,
    "officialSolution": null,
    "annotations": null,
    "unreadable": []
  },
  "knowledgePoints": [],
  "errorAnalysis": "",
  "correctApproach": "",
  "relatedKnowledge": [],
  "errorType": "",
  "confidence": 0.0,
  "uncertainties": []
}

The full schema is `question-analysis/v1` in `docs/06-ai-provider-architecture.md`.

AI-generated content must be validated before persistence.

Never blindly trust arbitrary model output.

---

## 6. AI Analysis Versioning

AI analysis must be versionable.

Never overwrite previous AI analysis without preserving history.

The system should support re-analysis when:

- the AI model changes
- the analysis prompt changes
- the user adds more information
- the user requests a new analysis

Every analysis version must record, at minimum:

- provider id and provider type
- model config id, requested model name, and the actual model reported by the provider (if any)
- prompt key, prompt version, and prompt template hash
- output schema version
- an input snapshot (source files/pages with roles, text fields, preprocessing parameters, model capability snapshot)
- trigger reason, token usage, latency, and failure reason if any

Successful analysis results are immutable. Only their review status may change.

---

## 7. Technology Stack

Backend:

- Java 21
- Spring Boot 3.x
- Maven
- Spring Web
- Spring Validation
- Spring Data JPA
- MySQL 8
- Flyway for database migrations

Frontend:

- Vue 3
- TypeScript
- Vite
- Element Plus
- Vue Router
- Pinia

AI:

- Multiple AI providers accessed through their APIs (e.g. OpenAI GPT, Anthropic Claude)
- First adapters: OpenAI Responses API and Anthropic Messages API (implement whichever has an available API key first, then the other)
- Other providers (e.g. Gemini, OpenAI-compatible Chat Completions vendors) may be added later as new adapters
- Business code depends only on a provider-neutral gateway, never on a vendor SDK
- Vendor SDKs may only be used inside their own adapter package
- Each model declares its capabilities in configuration (input modalities, structured output mode, streaming, image limits, context window, output limit); the system uses them to filter selectable models, reject unsupported requests, and prepare inputs
- Do not silently switch to another model when the selected model is unavailable
- Multimodal models capable of text and image input
- Structured outputs where appropriate

File storage:

- Local filesystem for development
- Keep storage implementation abstract so it can later be replaced by S3/MinIO/OSS

Development:

- Docker Compose for MySQL and other infrastructure when useful
- Git
- JUnit 5
- Testcontainers where appropriate

---

## 8. Architecture

Use a modular monolith.

Do NOT introduce microservices.

Backend modules should be organized around business domains:

- auth (reserved; not implemented in the MVP)
- workspace (LearningSpace and Subject)
- archive
- file
- question
- ai
- chat
- common

Avoid creating a large global package structure such as:

controller/
service/
repository/
entity/

when domain-based organization is more appropriate.

---

## 9. Core Domain Model

At minimum, design the following concepts:

User (designed but not implemented in the MVP)
LearningSpace
Subject
Archive
File
Question
AIAnalysis
ChatSession
ChatMessage
KnowledgePoint

Relationships must support:

- one LearningSpace containing many Archives
- one LearningSpace containing many optional Subjects
- Archives, Questions and KnowledgePoints optionally referencing a Subject of the same LearningSpace
- one Archive containing many Files
- Questions linked to their source files
- Questions belonging to a LearningSpace
- Questions having multiple AIAnalysis versions
- ChatSessions scoped to a LearningSpace, Archive, Question, or global context
- KnowledgePoints associated with Questions
- each Question having at most one confirmed AIAnalysis version

KnowledgePoints are flat tags within a LearningSpace. They may be summarized by AI or created by the user. Keep them concise: AI should reuse existing names, and at most 5 per question.

Do not over-normalize the database prematurely.

### 9.1 Reserved fields for a future review system

The `question` table reserves these nullable/defaulted columns:

- mastery_level
- review_count
- last_reviewed_at
- next_review_at

In the MVP they must not be mapped to API DTOs, shown in the UI, or used by any business logic. The review system will only operate on questions with a confirmed analysis.

---

## 10. Mathematics and 408

Mathematics and 408 share the same system infrastructure.

However, their AI analysis logic may differ.

Mathematics may emphasize:

- mathematical concepts
- solving methods
- derivation
- calculation
- reasoning
- condition interpretation

408 may emphasize:

- concepts
- conceptual distinctions
- algorithms
- data structures
- operating system mechanisms
- computer architecture
- networking mechanisms
- complexity analysis

Use configurable domain-specific analysis prompts/rules instead of hard-coded branches scattered throughout the code.

---

## 11. File and Image Handling

Uploaded files must have:

- unique internal ID
- original filename
- MIME type
- size
- storage location
- upload timestamp
- associated LearningSpace
- optional Archive association

Do not store large binary files directly in MySQL.

Store metadata in MySQL and binary content in the configured file storage layer.

Validate file type and size.

Do not trust the client-provided MIME type alone.

Original files are immutable. Thumbnails, rendered pages and AI input images are regenerable derived files.

Allowed types in the MVP: JPEG, PNG, WebP and PDF. HEIC/HEIF is not supported; the UI should ask the user to convert phone photos to JPG.

### 11.1 PDF scope in the MVP

Supported:

- text-based and scanned PDFs, up to 50 MB and 200 pages (configurable)
- viewing the original PDF and browsing rendered pages
- creating a question from selected pages, each page with its own role
- at most 6 sources (images and PDF pages combined) per question
- sending selected pages to AI as rendered images, so all providers behave the same

Not supported in the MVP:

- analyzing or summarizing a whole PDF at once
- AI splitting a page into multiple questions (the user gives a locate hint such as "Question 3" instead)
- region cropping (a `region` field is reserved)
- native PDF input to models
- text-layer extraction, full-text search, OCR indexing
- password-protected PDFs
- editing, annotating, merging or splitting PDFs

---

## 12. Security

Never hard-code API keys.

Use environment variables.

Provide:

.env.example

Never commit:

.env

Never expose any AI provider API key or base URL to the frontend.

All AI API calls must go through the backend.

Because there is no login in the MVP, the backend must listen on 127.0.0.1 only. Direct LAN/phone access is not supported in the MVP; phone photos are transferred to the computer first. Before exposing the app to a LAN or the internet, add at least a simple access password.

---

## 13. Development Rules

Before implementing a major feature:

1. Inspect the existing code.
2. Explain the intended change briefly.
3. Identify affected modules.
4. Implement the smallest complete version.
5. Run tests.
6. Fix failures.
7. Update documentation when architecture or behavior changes.

Do not rewrite working code unnecessarily.

Do not introduce dependencies without a clear reason.

Prefer simple solutions.

---

## 14. Testing

Every important business rule should have tests.

At minimum:

- domain/service unit tests
- controller/API tests for important endpoints
- AI output validation tests
- file validation tests
- database integration tests for important persistence behavior

For AI behavior, do not rely exclusively on live API tests.

Use mocked AI responses for deterministic automated tests.

---

## 15. API Design

Use RESTful APIs.

Return consistent response structures.

Validate request parameters at the boundary.

Use appropriate HTTP status codes.

Do not expose internal database entities directly as API contracts.

Use DTOs.

---

## 16. Frontend Principles

The UI should prioritize:

- simple navigation
- fast file upload
- easy question review
- readable AI analysis
- convenient AI interaction
- clear separation between LearningSpaces

Primary navigation should include:

- Home
- Archives
- Questions
- AI Assistant
- Learning Profile

The current LearningSpace should always be visually clear.

In the MVP, "AI Assistant" and "Learning Profile" are shown as disabled navigation items labeled as planned.

---

## 17. MVP Scope

The first MVP should only implement:

1. LearningSpace creation/selection (including lightweight Subjects)
2. Archive creation
3. File/image/PDF upload (PDF scope in 11.1)
4. Question creation from images/PDF pages with source roles
5. AI analysis of question images, selectable among multiple models
6. Structured AI analysis result with versioning, confidence level and user confirmation
7. Question list
8. Archive browsing
9. Question detail page
10. Basic AI chat scoped to the current question

Do NOT implement initially:

- login/registration and multi-user support
- AI automatic question splitting
- review system (only the reserved fields in 9.1)
- learning profile

- RAG
- vector database
- agent framework
- Redis
- microservices
- complex recommendation systems
- social features
- mobile application
- advanced learning analytics

These may be added later after the core workflow is proven.

---

## 18. Development Strategy

Build vertically, not horizontally.

Each milestone should produce a usable feature.

Preferred order:

1. Project skeleton
2. LearningSpace
3. Archive and file upload
4. Question management
5. AI question analysis
6. AI chat
7. Question/knowledge relationships
8. Learning profile
9. Advanced retrieval/RAG

Do not implement all backend infrastructure before building a usable workflow.

---

## 19. Important Product Constraint

The product should optimize for actual personal use rather than feature count.

The primary workflow should be:

Upload material
→ AI understands it
→ Identify question
→ Analyze mistake
→ Save structured result
→ User interacts with AI
→ Revisit later
→ Build long-term learning profile

Every new feature should be evaluated against this workflow.

Avoid building features merely because they are technically interesting.