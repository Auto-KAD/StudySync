# StudySync

## Student Resource Management and AI-Assisted Academic Study System

StudySync is a Java Swing desktop application designed to centralize academic resources, organize them by subject and category, and provide a local AI assistant capable of answering questions from selected academic documents.

The working prototype combines **Java Swing**, **OneDrive-synchronized local storage**, **Apache PDFBox**, **Apache POI**, and **Ollama with Qwen3.5 4B** for local multimodal AI.

The broader system is designed to fit within a Microsoft 365 academic ecosystem involving **Microsoft Teams, OneDrive/SharePoint, and Power Automate**.

> **Implementation boundary:** The current prototype uses a local OneDrive-synchronized folder and local Ollama/Qwen inference. Teams and Power Automate are part of the integration architecture and workflow design. They should be described as fully implemented only where corresponding tenant/workflow deployment has actually been completed.

---

## 1. Problem Statement

Academic resources are commonly distributed across Teams, OneDrive, teacher-shared folders, lecture slides, laboratory material, textbooks, syllabi, and previous-year papers. Students therefore spend time locating, organizing, opening, and interpreting material.

StudySync provides a unified interface to:

- browse subjects
- view resources
- automatically classify files
- search and filter material
- open resources
- select one or multiple resources
- ask document-grounded AI questions
- ask contextual follow-ups
- analyze supported visual content
- copy AI responses

---

## 2. Objectives

### Primary
1. Centralize academic resources.
2. Reduce time spent locating material.
3. Automatically categorize resources.
4. Provide search and filtering.
5. Provide document-grounded AI assistance.
6. Support PDF, DOCX, and PPTX.
7. Support selected visual document content.
8. Keep current AI processing local.

### Secondary
- Support Microsoft 365 integration.
- Support Power Automate-based resource workflows.
- Demonstrate modular Software Engineering design.
- Provide a foundation for future institutional deployment.

---

## 3. Core Features

### Subject Management

Example:

```text
Subjects
├── IVP
├── OS
├── SE
└── ST
```

### Resource Classification

Six categories:

```text
LAB
LECTURE_SLIDES
SYLLABUS_POLICY
BOOKS
PREVIOUS_YEAR_PAPERS
MISCELLANEOUS
```

### Document Processing

```text
Selected File
     |
     v
DocumentService
     |
     v
DocumentProcessorFactory
     |
     +--> PDFProcessor
     +--> PPTXProcessor
     +--> DOCXProcessor
     |
     v
DocumentModel
```

The unified model can contain text, sections, tables, metadata, and supported visual content.

### Local AI

```text
StudySync
   |
AIService
   |
LocalAI
   |
Ollama
   |
Qwen3.5 4B
```

No cloud AI API is required for the current AI path.

### Multimodal AI

```text
PDF
 ↓
PDFProcessor
 ↓
bounded visual extraction
 ↓
DocumentImage
 ↓
AIRequest
 ↓
Qwen3.5 4B
```

Current protective limits include a maximum of 5 rendered visual pages and an Ollama context setting of 8192.

### Follow-Up Conversations

Processed document models can be reused during follow-up questions so the same files do not need to be unnecessarily reparsed.

---

## 4. Microsoft 365 Ecosystem

The intended broader workflow is:

```text
Microsoft Teams
       |
       v
OneDrive / SharePoint
       |
       v
Power Automate
       |
       +--------------------+
       |                    |
       v                    v
Resource Routing       Notifications
       |
       v
Student OneDrive
       |
       v
StudySync
```

### Microsoft Teams

Provides the course communication and academic resource-distribution environment.

### OneDrive / SharePoint

Provides the cloud storage and synchronization layer. The current prototype consumes the student's locally synchronized OneDrive directory.

### Power Automate

Can automate workflows such as:

```text
New resource uploaded
       ↓
Power Automate trigger
       ↓
Identify course/resource
       ↓
Route or synchronize
       ↓
Notify students
       ↓
StudySync refresh
```

Possible workflows include new-resource notifications, updated-material notifications, and course-specific routing.

These are integration workflows and should be marked **proposed/integration-ready** unless the corresponding Power Automate flows are deployed.

---

## 5. Three-Part Application Architecture

The application-level Java structure is intentionally limited to three major classes:

```text
                 STUDENT
                    |
                    v
              +-----------+
              |    UI     |
              +-----+-----+
                    |
                    v
              +-----------+
              | StudySync |
              +-----+-----+
                    |
             +------+------+
             |             |
             v             v
       +-----------+   AI workflow
       | DataFetch |
       +-----+-----+
             |
             v
        Local Files /
        OneDrive Sync
```

### StudySync.java
Central application controller. Coordinates user actions, selected resources, document processing, AI workflow, and conversation state.

### DataFetch.java
Data/file layer. Handles resource discovery, recursive scanning, subject/resource retrieval, filtering-related data access, local availability, and file opening.

### UI.java
Presentation layer. Handles Swing components, panels, dialogs, styling, rendering, and user-facing AI presentation.

Supporting document and AI classes remain separate.

---

## 6. Supporting Classes

```text
AIRequest
AIResponse
AIService
LocalAI

DocumentModel
DocumentImage
DocumentProcessor
DocumentProcessorFactory
DocumentService
DocumentContextBuilder
PDFProcessor
PPTXProcessor
DOCXProcessor

ResourceCluster
```

---

## 7. Technology Stack

| Layer | Technology |
|---|---|
| Language | Java |
| UI | Java Swing |
| Storage source | OneDrive-synchronized local folder |
| PDF processing | Apache PDFBox |
| DOCX/PPTX | Apache POI |
| AI abstraction | AIService |
| Local AI runtime | Ollama |
| AI model | Qwen3.5 4B |
| Microsoft ecosystem | Teams / OneDrive / Power Automate |

---

## 8. Design Principles

- Separation of concerns
- Encapsulation
- Abstraction
- Polymorphism
- Modularity
- Local-first AI processing
- Privacy-aware design
- Controlled resource usage

`AIService` abstracts the AI provider, while `DocumentProcessor` abstracts document types.

---

## 9. End-to-End Workflow

```text
Microsoft 365 ecosystem
        |
        v
OneDrive synchronized resources
        |
        v
DataFetch
        |
        v
StudySync
        |
        v
UI
        |
        v
Student selects resources
        |
        v
DocumentService
        |
        v
DocumentModel
        |
        v
DocumentContextBuilder
        |
        v
AIRequest
        |
        v
LocalAI
        |
        v
Ollama / Qwen3.5 4B
        |
        v
AIResponse
        |
        v
UI
        |
        v
Student
```

---

## 10. Security and Privacy

The local AI architecture reduces the need to transmit academic documents to external AI providers.

Relevant considerations include:

- local file permissions
- Microsoft 365 permissions
- shared-resource access
- protection of configuration files
- avoiding hard-coded credentials
- controlled access to course material

---

## 11. Limitations

- Teams/Power Automate deployment depends on institutional Microsoft 365 permissions.
- Direct cloud API synchronization is not required by the current prototype.
- Local AI speed depends on available CPU/RAM/GPU.
- Multimodal rendering is deliberately bounded.
- Some vector-only PDF content may not be detected as raster visual content.
- Model response quality depends on the selected model and supplied context.

---

## 12. Future Enhancements

- Microsoft Graph integration
- direct Teams/SharePoint ingestion
- deployed Power Automate synchronization
- query-aware visual page selection
- OCR for scanned documents
- semantic/vector search
- resource version tracking
- quiz generation
- revision plans
- teacher-side publishing
- institutional deployment

---

## 13. Conclusion

StudySync combines academic resource management, document processing, local multimodal AI, and Microsoft 365 workflow design in one system.

The application-level structure is:

```text
StudySync.java
DataFetch.java
UI.java
```

with dedicated supporting classes for document processing, classification, and AI.

---

## 14. Learning Outcomes

The project demonstrates practical learning in:

1. Java and Object-Oriented Programming.
2. Software architecture and modular design.
3. Swing GUI development.
4. File and resource management.
5. PDF/DOCX/PPTX processing.
6. Interface-based service abstraction.
7. Local AI integration.
8. Multimodal processing.
9. UML and Software Engineering modelling.
10. Separation of concerns.
11. Regression and functional testing.
12. Microsoft 365 workflow concepts.
13. Power Automate workflow design.
14. Privacy-aware AI architecture.
15. Performance and memory management.
