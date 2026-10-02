# StudySync Refactoring & Local AI Architecture Plan

## 1. Purpose

This document is the implementation blueprint for refactoring **StudySync**, the Student Resource Management System, into a modular software architecture suitable for:

- Software Engineering implementation
- UML diagrams
- Use-case modelling
- Class diagrams
- Sequence diagrams
- Activity diagrams
- State-machine diagrams
- Maintainable Java code
- Local AI integration
- Future extension without returning to a monolithic `StudySync.java`

The project is a plain Java desktop application using Swing. It currently integrates with a local OneDrive folder containing subject-wise academic resources.

The immediate goal is **not** to rewrite the entire application at once.

The goal is to incrementally extract responsibilities from the current `StudySync.java` while preserving working functionality after every phase.

---

# 2. Current Project Context

`StudySync.java` currently contains substantial functionality, including:

- Swing UI construction
- Subject navigation
- Resource display
- Resource cards
- Resource selection
- OneDrive/local folder access
- Resource scanning
- Resource classification coordination
- AI interaction
- Search/filter behaviour
- Opening resources
- Application event handling

The resource classification component is already separated into:

```text
ResourceCluster.java
```

Its categories are:

- LAB
- LECTURE_SLIDES
- SYLLABUS_POLICY
- BOOKS
- PREVIOUS_YEAR_PAPERS
- MISCELLANEOUS

`ResourceCluster.java` should remain a dedicated classification component.

The project previously used Gemini AI. The project is now being migrated to a fully local AI architecture to avoid cloud API billing, quota, and network dependency.

---

# 3. Local AI Decision

The selected local model is:

```text
Qwen3.5 4B
```

Runtime:

```text
Ollama 0.35.0
```

Model:

```text
qwen3.5:4b
```

Quantization:

```text
Q4_K_M
```

The model supports text and vision, as well as thinking. For normal StudySync requests, thinking must explicitly be disabled:

```json
"think": false
```

Testing has already confirmed that Java can communicate with Ollama and Qwen3.5 4B successfully with thinking disabled.

The local AI pipeline is:

```text
StudySync
    ↓
LocalAI.java
    ↓
Ollama localhost API
    ↓
Qwen3.5 4B
```

---

# 4. Critical Migration Rule

Do **not** immediately delete or rewrite the existing Gemini implementation.

Migration must happen in stages.

Current migration state:

```text
GeminiAI.java       KEEP temporarily
apiconfig.java      KEEP temporarily
LocalAI.java        ACTIVE DEVELOPMENT
StudySync.java      CURRENT APPLICATION
```

Target:

```text
StudySync
   ↓
AIService
   ↓
LocalAI
   ↓
Ollama
   ↓
Qwen3.5 4B
```

Only after LocalAI and document processing have been successfully integrated should obsolete Gemini components be removed.

---

# 5. Architectural Principles

## 5.1 Single Responsibility

Each class should have one clear primary responsibility.

`LocalAI` should communicate with Ollama. It should not:

- construct Swing panels
- scan OneDrive
- classify files
- parse PDFs
- build UI cards

## 5.2 Separation of Concerns

Separate:

```text
Presentation
Business Logic
Data/File Access
Document Processing
AI Integration
Configuration
```

## 5.3 Encapsulation

Classes should expose only operations required by other components. Internal implementation details should remain private.

## 5.4 Dependency Direction

Prefer:

```text
UI
 ↓
Business Logic
 ↓
Services / Data Access
```

rather than allowing every class to directly manipulate every other class.

## 5.5 Replaceability

Components should be replaceable where practical.

Example:

```text
AIService
    ▲
    │
LocalAI
```

The UI depends on the abstraction rather than directly depending on a specific AI implementation.

---

# 6. Target Class Structure

The intended logical structure is:

```text
StudySync
│
├── Application
│   └── StudySyncApp.java
│
├── UI
│   ├── MainWindow.java
│   ├── SubjectPanel.java
│   ├── ResourcePanel.java
│   ├── ResourceCard.java
│   ├── AIPanel.java
│   └── UIUtils.java
│
├── OneDrive
│   ├── OneDriveManager.java
│   └── ResourceScanner.java
│
├── Resources
│   ├── Resource.java
│   ├── ResourceManager.java
│   └── ResourceCluster.java
│
├── Document Processing
│   ├── DocumentProcessor.java
│   ├── PDFProcessor.java
│   ├── PPTXProcessor.java
│   ├── DOCXProcessor.java
│   └── DocumentModel.java
│
├── AI
│   ├── AIService.java
│   ├── LocalAI.java
│   ├── AIRequest.java
│   └── AIResponse.java
│
└── Configuration
    └── AppConfig.java
```

Do not move every file into Java packages in one destructive operation. Preserve a runnable application throughout the refactoring.

---

# 7. Application Layer

## StudySyncApp.java

Responsibilities:

- Application entry point
- Start the Swing Event Dispatch Thread
- Create `MainWindow`
- Show the application

Conceptually:

```java
public class StudySyncApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }
}
```

It should not contain application business logic.

Relationship:

```text
StudySyncApp
      │
      │ creates
      ▼
MainWindow
```

---

# 8. UI Layer

The current `StudySync.java` contains too much UI code. Separate the UI into logical components.

## MainWindow.java

Responsibilities:

- Main JFrame
- Main layout
- Coordination of major UI panels

Major components:

```text
SubjectPanel
ResourcePanel
AIPanel
```

Relationship:

```text
MainWindow
 ├── SubjectPanel
 ├── ResourcePanel
 └── AIPanel
```

This can be represented as composition in UML if the lifecycle of the panels is owned by `MainWindow`.

## SubjectPanel.java

Responsibilities:

- Display subjects
- Handle subject selection
- Notify the application when the selected subject changes

Example subjects:

```text
IVP
OS
SE
ST
```

It should not scan the filesystem itself.

## ResourcePanel.java

Responsibilities:

- Display resources for the selected subject
- Display resource clusters/categories
- Manage visible resource cards
- Refresh resource display

It obtains resource information through `ResourceManager`.

## ResourceCard.java

Represents one resource visually.

Desired concept:

```text
☐ Resource_Name.pdf       [OPEN]
```

Handles:

- Checkbox state
- Resource name
- Resource icon/type
- Open action
- Selection notification

It should not know how OneDrive scanning works.

## AIPanel.java

Responsibilities:

- AI query text field
- ASK AI button
- Selected resource count
- AI response display
- Trigger AI analysis

Desired UI:

```text
StudySync AI

2 resources selected

[ Ask AI about selected resources... ] [ ASK AI ]

AI Response:
...
```

`AIPanel` should not implement HTTP communication with Ollama.

Target:

```text
AIPanel
   ↓
AIService
```

## UIUtils.java

Optional helper for repeated Swing operations such as:

- Standard fonts
- Buttons
- Borders
- Dialog helpers
- Common formatting

Do not put business logic here.

---

# 9. OneDrive/File Access Layer

## OneDriveManager.java

Responsibilities:

- Locate/configure local OneDrive root
- Locate subject folders
- Provide filesystem-level resource access
- Open resources when requested

Possible operations:

```java
getRootDirectory()
getSubjectFolders()
getSubjectFolder(String subject)
openResource(File file)
```

It should not classify resources.

Target:

```text
ResourceManager
      ↓
OneDriveManager
      ↓
Local OneDrive/File System
```

## ResourceScanner.java

Responsibilities:

- Scan a subject directory
- Find files/resources
- Return discovered files

Example:

```java
List<File> scanSubject(File subjectFolder)
```

It should not:

- Build Swing components
- Call Ollama
- Decide AI responses

Target:

```text
OneDriveManager
       ↓
ResourceScanner
       ↓
Files
```

Whether `OneDriveManager` directly owns the scanner or `ResourceManager` coordinates both should be decided from the existing implementation. Avoid unnecessary duplication.

---

# 10. Resource Domain Layer

## Resource.java

Introduce a domain object representing an academic resource.

Suggested attributes:

```text
file
name
category
```

Possible future attributes:

```text
extension
size
subject
path
```

Conceptually:

```java
public class Resource {

    private final File file;
    private final String name;
    private final ResourceCluster.Category category;

    ...
}
```

This lets the application work with `Resource` objects instead of passing raw `File` objects everywhere.

## ResourceManager.java

This becomes the resource business-logic component.

Responsibilities:

- Obtain resources for a subject
- Convert discovered files into `Resource` objects
- Coordinate classification
- Search resources
- Filter resources
- Provide resources to the UI

Target:

```text
ResourcePanel
      ↓
ResourceManager
      ├── OneDriveManager
      ├── ResourceScanner
      └── ResourceCluster
```

This is an important class for the UML class diagram because it represents business logic rather than presentation.

## ResourceCluster.java

Keep the existing classification logic as a dedicated component.

Categories:

```text
LAB
LECTURE_SLIDES
SYLLABUS_POLICY
BOOKS
PREVIOUS_YEAR_PAPERS
MISCELLANEOUS
```

Flow:

```text
File
 ↓
ResourceCluster.classify()
 ↓
Category
```

Do not put UI logic into this class.

---

# 11. AI Architecture

## AIService.java

Create an interface.

Conceptually:

```java
public interface AIService {

    String ask(String query);

    String analyzeFiles(
        List<File> files,
        String query
    );
}
```

Exact signatures can be refined during implementation.

Purpose:

The UI depends on `AIService`, not directly on `LocalAI`.

Target:

```text
AIPanel
    ↓
AIService
    ▲
    │
LocalAI
```

## LocalAI.java

Responsibilities:

- Communicate with local Ollama
- Send prompts
- Receive model responses
- Configure Qwen3.5
- Keep thinking disabled for normal StudySync requests
- Handle local AI communication errors

Ollama endpoint:

```text
http://localhost:11434/api/chat
```

Model:

```text
qwen3.5:4b
```

Request configuration:

```json
{
    "stream": false,
    "think": false,
    "keep_alive": "30m"
}
```

`LocalAI` must not:

- Build Swing UI
- Scan OneDrive
- Classify resources
- Manipulate UI components

## AIRequest.java and AIResponse.java

These are optional abstractions.

Potential structure:

```text
AIRequest
 ├── query
 ├── selected resources
 └── context

AIResponse
 ├── answer
 └── optional metadata
```

Do not create these solely for UML decoration. Introduce them if they become useful as the AI workflow grows.

---

# 12. Document Processing Layer

The local AI must eventually understand:

- PDF
- PPTX
- DOCX
- Images
- Tables
- Diagrams
- Charts
- Rendered pages/slides

The document-processing layer must remain independent from AI communication.

Target:

```text
Resource
   ↓
DocumentProcessor
   ↓
DocumentModel
   ↓
AIService / LocalAI
```

## DocumentProcessor.java

Use an interface:

```java
public interface DocumentProcessor {

    boolean supports(File file);

    DocumentModel process(File file) throws Exception;
}
```

Implementations:

```text
DocumentProcessor
       ▲
       │
 ┌─────┼───────────────┐
 │     │               │
PDF   PPTX            DOCX
Processor Processor   Processor
```

This provides a real generalization/realization relationship for UML.

## PDFProcessor.java

Use Apache PDFBox.

Responsibilities:

- Read PDF
- Extract text
- Identify pages
- Extract useful structural information
- Render selected pages when visual analysis is required

Do not render every page automatically.

Preferred strategy:

```text
PDF
 ↓
Text extraction
 ↓
Relevant/difficult page?
 ↓ yes
Render page
 ↓
Qwen vision
```

## PPTXProcessor.java

Use Apache POI.

Responsibilities:

- Extract slide text
- Extract slide structure
- Extract tables where practical
- Identify embedded visual elements
- Prepare visual content when required

## DOCXProcessor.java

Use Apache POI.

Responsibilities:

- Extract paragraphs
- Extract headings where practical
- Extract tables
- Identify embedded images
- Provide structured document content

## DocumentModel.java

Create a common internal representation.

Conceptual model:

```text
DocumentModel
 ├── fileName
 ├── fileType
 └── pages/slides
```

A page/slide can contain:

```text
text
tables
images
visual information
```

Possible conceptual structure:

```text
DocumentModel
    │
    ├── DocumentPage
    │      ├── pageNumber
    │      ├── text
    │      ├── tables
    │      └── images
    │
    └── metadata
```

This allows PDF, PPTX and DOCX processors to produce a common format.

---

# 13. Hybrid Document + Vision Strategy

Do not send every page/slide as an image to Qwen.

Use a hybrid strategy:

```text
Original Resource
      ↓
Deterministic Parser
      ↓
Text / Tables / Structure
      ↓
Identify visually important content
      ↓
Render only relevant pages/slides
      ↓
Qwen Vision
      ↓
Combined Context
      ↓
AI Answer
```

Vision is especially useful for:

- Diagrams
- Charts
- Screenshots
- Mathematical figures
- Complicated tables
- Image-heavy slides
- Scanned pages

This keeps processing lighter on the M2 Air.

---

# 14. Final AI Resource Flow

The intended user workflow:

```text
Student selects subject
        ↓
ResourcePanel loads resources
        ↓
Student selects one or more resources
        ↓
AIPanel updates selected count
        ↓
Student enters query
        ↓
AIPanel sends request to AIService
        ↓
ResourceManager provides selected resources
        ↓
DocumentProcessor processes files
        ↓
DocumentModel created
        ↓
Relevant visual content prepared
        ↓
LocalAI receives structured context
        ↓
Ollama
        ↓
Qwen3.5 4B
        ↓
AI response
        ↓
AIPanel displays answer
```

---

# 15. UML Relationships to Showcase

## Association

Example:

```text
ResourcePanel ─── ResourceManager
```

A panel uses the manager.

## Composition

Example:

```text
MainWindow ◆── AIPanel
MainWindow ◆── SubjectPanel
MainWindow ◆── ResourcePanel
```

The main window owns these UI components.

## Generalization / Realization

Example:

```text
DocumentProcessor
       ▲
       │
 ┌─────┼────────────┐
 │     │            │
PDF   PPTX         DOCX
```

Also:

```text
AIService
    ▲
    │
 LocalAI
```

## Dependency

Example:

```text
ResourceManager
      - - - - > ResourceCluster
```

ResourceManager depends on the classification component.

---

# 16. Use Cases

Primary student use cases:

```text
Browse Subjects
View Resources
Search Resources
Filter Resources
Open Resource
Select Resources
Ask StudySync AI
View AI Response
```

Possible future use cases:

```text
View Resource Details
Analyze Multiple Resources
Ask Question About Selected Material
```

---

# 17. Sequence Diagram Candidates

## Sequence 1: Browse Resources

```text
Student
 ↓
SubjectPanel
 ↓
ResourcePanel
 ↓
ResourceManager
 ↓
OneDriveManager
 ↓
ResourceScanner
 ↓
ResourceManager
 ↓
ResourcePanel
```

## Sequence 2: Classify Resources

```text
ResourceManager
 ↓
ResourceScanner
 ↓
ResourceCluster
 ↓
Resource
```

## Sequence 3: Open Resource

```text
Student
 ↓
ResourceCard
 ↓
ResourceManager / OneDriveManager
 ↓
Operating System
```

## Sequence 4: Ask AI About Resources

```text
Student
 ↓
AIPanel
 ↓
AIService
 ↓
ResourceManager
 ↓
DocumentProcessor
 ↓
DocumentModel
 ↓
LocalAI
 ↓
Ollama
 ↓
Qwen3.5 4B
 ↓
LocalAI
 ↓
AIPanel
 ↓
Student
```

This should be one of the primary sequence diagrams.

---

# 18. Activity Diagram Candidate: AI Analysis

```text
Start
  ↓
Select Subject
  ↓
Display Resources
  ↓
Select Resources
  ↓
Enter Query
  ↓
Validate Selection
  ↓
Process Documents
  ↓
Extract Text / Tables
  ↓
Visual Content Required?
  ├── No → Build Context
  │
  └── Yes → Render Relevant Visuals
                 ↓
             Build Context
                 ↓
             Send to LocalAI
                 ↓
              Qwen3.5
                 ↓
            Receive Answer
                 ↓
            Display Answer
                 ↓
                End
```

---

# 19. State Machine Candidate

A resource can be represented as:

```text
Discovered
    ↓
Classified
    ↓
Displayed
    ↓
Selected
    ↓
Processing
    ↓
Analyzed
```

Possible transitions:

```text
Displayed → Opened
Displayed → Selected
Selected → Deselected
Selected → Processing
Processing → Analyzed
```

---

# 20. Refactoring Phases

## Phase 1: Preserve Current Working State

Before refactoring:

- Ensure current StudySync runs.
- Make a backup/commit.
- Do not modify Gemini functionality yet.
- Keep LocalAI standalone.

## Phase 2: Extract OneDrive/File Logic

Create:

```text
OneDriveManager.java
ResourceScanner.java
```

Move only filesystem-related functionality.

Verify:

- Subjects still appear.
- Resources still appear.
- Opening resources still works.

## Phase 3: Extract Resource Domain Logic

Create:

```text
Resource.java
ResourceManager.java
```

Move:

- Resource creation
- Classification coordination
- Search
- Filtering

Verify UI behaviour.

## Phase 4: Extract UI Components

Create:

```text
MainWindow.java
SubjectPanel.java
ResourcePanel.java
ResourceCard.java
AIPanel.java
UIUtils.java
```

Do not change visual behaviour unnecessarily.

The objective is structural separation, not redesign.

## Phase 5: Establish AI Abstraction

Create:

```text
AIService.java
```

Make:

```text
LocalAI.java implements AIService
```

Test LocalAI independently.

Do not delete Gemini yet.

## Phase 6: Add Document Processing

Create:

```text
DocumentProcessor.java
PDFProcessor.java
PPTXProcessor.java
DOCXProcessor.java
DocumentModel.java
```

Test each processor independently before connecting it to the UI.

## Phase 7: Connect Local AI to Documents

Implement:

```text
Resource selection
       ↓
Document processing
       ↓
DocumentModel
       ↓
LocalAI
       ↓
Qwen
```

Test with:

- PDF
- PPTX
- DOCX
- image-heavy resources

## Phase 8: Replace Gemini Usage

Only after LocalAI + document processing is stable:

Replace:

```text
GeminiAI.analyzeFiles(...)
```

with the new:

```text
AIService / LocalAI
```

integration.

## Phase 9: Remove Gemini

After successful migration, remove:

```text
GeminiAI.java
apiconfig.java
```

only if no remaining code depends on them.

Remove old API-specific configuration and imports.

## Phase 10: Final Architecture Cleanup

Check:

- No duplicated responsibilities
- No UI code inside AI classes
- No AI code inside file scanners
- No filesystem code inside UI panels
- No obsolete Gemini references
- No unnecessary static state
- No giant methods
- Clear dependencies
- Clear class names

---

# 21. Important Do-Not-Do Rules

Antigravity must follow these rules during implementation.

1. Do not rewrite the entire application in one step.
2. Do not delete GeminiAI before LocalAI integration is verified.
3. Do not put document parsing into `StudySyncApp`.
4. Do not put document parsing into `AIPanel`.
5. Do not put Swing UI code into `LocalAI`.
6. Do not put OneDrive scanning into `ResourceCard`.
7. Do not put resource classification into `LocalAI`.
8. Do not create unnecessary classes solely for UML decoration.
9. Do not break currently working UI functionality unnecessarily.
10. Do not reintroduce Apache Tika.

Tika has intentionally been removed from the project.

---

# 22. Current LocalAI Validation Status

LocalAI has already been validated independently.

Known working environment:

```text
Ollama: 0.35.0
Model: qwen3.5:4b
Quantization: Q4_K_M
Java → Ollama: WORKING
think=false: WORKING
```

A successful local request uses:

```json
{
    "model": "qwen3.5:4b",
    "messages": [
        {
            "role": "user",
            "content": "Say hello in exactly one short sentence."
        }
    ],
    "stream": false,
    "think": false
}
```

The model returned:

```text
Hello!
```

The Java `LocalAI.java` test also successfully returned a StudySync-specific response.

Therefore, the local AI communication layer is considered validated.

---

# 23. Final Target Architecture

```text
                         StudySyncApp
                              │
                              ▼
                         MainWindow
                              │
             ┌────────────────┼────────────────┐
             │                │                │
             ▼                ▼                ▼
       SubjectPanel      ResourcePanel       AIPanel
                              │                │
                              ▼                ▼
                       ResourceManager      AIService
                              │                ▲
                  ┌───────────┴──────────┐     │
                  ▼                      ▼     │
          OneDriveManager       ResourceScanner│
                  │                      │      │
                  └──────────┬───────────┘      │
                             ▼                  │
                          Resource              │
                             │                  │
                             ▼                  │
                      ResourceCluster           │
                                                │
                                                ▼
                                           LocalAI
                                                │
                                                ▼
                                             Ollama
                                                │
                                                ▼
                                          Qwen3.5 4B


                    Document Processing Layer

                       DocumentProcessor
                              ▲
              ┌───────────────┼───────────────┐
              │               │               │
              ▼               ▼               ▼
        PDFProcessor    PPTXProcessor    DOCXProcessor
              │               │               │
              └───────────────┼───────────────┘
                              ▼
                       DocumentModel
                              │
                              ▼
                           LocalAI
```

---

# 24. Success Criteria

The refactoring is successful when:

- `StudySync.java` no longer acts as the entire application.
- UI responsibilities are separated.
- OneDrive/file access is separated.
- Resource business logic is separated.
- Resource classification remains encapsulated.
- AI communication is separated.
- Document processing is separated.
- Local Qwen inference works without cloud APIs.
- PDF/PPTX/DOCX processing has dedicated implementations.
- UML relationships correspond to actual code relationships.
- Sequence diagrams can be derived directly from real application flows.
- The application remains functional after each migration phase.

The architecture should be understandable by another developer without needing to read a 2,000+ line class first.

---

# 25. Implementation Philosophy

**Refactor first, enhance second.**

Do not simultaneously:

```text
refactor architecture
+
change UI
+
add document parsing
+
add AI
+
change resource logic
```

Instead:

```text
Existing working application
        ↓
Extract responsibility
        ↓
Compile
        ↓
Run
        ↓
Verify
        ↓
Next responsibility
```

This makes regressions easy to locate and keeps the project demonstrable throughout development.

The final system should not merely work. It should have an architecture that can be explained clearly in the Software Engineering documentation and defended during a viva.
