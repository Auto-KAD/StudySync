# StudySync: Three-Part Disintegration Execution Plan

## Objective

Refactor the currently working `StudySync.java` into exactly **three
application-level classes**:

``` text
StudySync.java
DataFetch.java
UI.java
```

No additional application/controller classes will be introduced.

The existing supporting classes remain untouched:

``` text
AIRequest.java
AIResponse.java
AIService.java
LocalAI.java

DocumentModel.java
DocumentImage.java
DocumentProcessor.java
DocumentProcessorFactory.java
DocumentService.java
DocumentContextBuilder.java
PDFProcessor.java
PPTXProcessor.java
DOCXProcessor.java

ResourceCluster.java
```

The objective is **not** to redesign the working application. It is a
controlled separation of responsibilities so that the Software
Engineering diagrams can represent a clear architecture while preserving
the exact current functionality.

------------------------------------------------------------------------

# 1. Target Architecture

The final application-level architecture will be:

``` text
                         STUDENT
                            |
                            v
                     +-------------+
                     |     UI      |
                     | Presentation|
                     +------+------+
                            |
                            v
                     +-------------+
                     |  StudySync  |
                     | Application |
                     | Controller  |
                     +------+------+
                            |
                +-----------+-----------+
                |                       |
                v                       v
         +-------------+         +-------------+
         | DataFetch   |         | Local AI    |
         | Data Layer  |         | via         |
         |             |         | AIService   |
         +------+------+         +-------------+
                |
                v
       Local OneDrive Folder
                |
                v
       Files / Subjects / Resources

Document processing remains behind StudySync:

StudySync
    |
    +--> DocumentService
    |       |
    |       +--> PDFProcessor
    |       +--> PPTXProcessor
    |       +--> DOCXProcessor
    |
    +--> DocumentContextBuilder
    |
    +--> AIService
            |
            +--> LocalAI
                    |
                    +--> Ollama
                            |
                            +--> Qwen3.5:4B
```

## Responsibility rule

### `StudySync.java`

Remains the **central application controller**.

It keeps: - application state - selected resources - AI state - document
processing state - AI conversation logic - event coordination - existing
startup behavior - calls to `DocumentService` - calls to
`DocumentContextBuilder` - calls to `AIService / LocalAI`

### `DataFetch.java`

Owns only **resource acquisition and file-data operations**.

It will handle: - selecting the OneDrive folder - recursively scanning
files - identifying subject folders - loading resource files -
refreshing resource data - file type/category data that belongs to
resource retrieval - opening files if that operation is treated as
resource/file access

It must NOT contain: - Swing layout construction - AI logic - Ollama
calls - document parsing - AI prompt construction

### `UI.java`

Owns only **presentation and reusable Swing UI construction**.

It will handle: - visual component creation - panels - buttons - text
fields - combo boxes - scroll panes - borders - colors/theme helpers -
resource card rendering - AI panel rendering - display/update methods
that only manipulate UI

It must NOT contain: - OneDrive scanning - recursive file discovery -
PDF/DOCX/PPTX parsing - Ollama communication - AI context generation

------------------------------------------------------------------------

# 2. Critical Preservation Rule

## Do NOT perform a big-bang rewrite.

The current application is working.

Therefore:

``` text
Current working StudySync
        |
        v
Extract one responsibility
        |
        v
Compile
        |
        v
Run
        |
        v
Test
        |
        v
Continue
```

Never move multiple unrelated blocks simultaneously without compiling.

The refactor is successful only if the application behaves exactly as
before.

------------------------------------------------------------------------

# 3. Current StudySync Dependency Baseline

The current `StudySync.java` is approximately 2,575 lines and contains:

-   Swing UI
-   resource loading
-   category/filter logic
-   AI selection
-   document processing calls
-   local AI calls
-   multimodal image selection
-   AI conversation rendering
-   Markdown-to-HTML conversion
-   file opening
-   utility methods
-   application startup

Important existing fields include:

``` java
private List<File> currentFiles;

private final DocumentService documentService;
private final DocumentContextBuilder documentContextBuilder;
private final AIService localAI;

private final Set<String> selectedAiFiles;
```

These fields are part of the working system and must not be removed
accidentally during extraction.

------------------------------------------------------------------------

# 4. Phase 0: Create a Safe Baseline

Before changing code:

## 4.1 Verify current build

Run:

``` bash
rm -rf antigravity-build
mkdir -p antigravity-build

javac -cp "lib/*" -d antigravity-build src/*.java
```

Then:

``` bash
java -cp "antigravity-build:lib/*" StudySync
```

## 4.2 Verify manually

Confirm:

-   application launches
-   OneDrive folder selection works
-   subjects load
-   resources load
-   resource categories work
-   search/filter works
-   OPEN works
-   resource selection works
-   AI panel works
-   single-resource AI works
-   multi-resource AI works
-   multimodal questions work
-   follow-up questions work
-   cached resources work
-   Copy works
-   Close works

Only proceed after the baseline is confirmed.

------------------------------------------------------------------------

# 5. Phase 1: Create `DataFetch.java`

Create the new class first.

Do NOT immediately remove the original methods from `StudySync.java`.

Initially, `DataFetch` should contain extracted logic with
behavior-preserving wrappers.

## Candidate responsibilities from current StudySync

The current methods most closely related to DataFetch are:

``` text
loadSubjects(File oneDriveFolder)
loadCategories()
refreshResources()
getFilesRecursively(File folder)
getFileType(File file)
getCategory(File file)
matchesType(File file, ...)
openFile(File file)
```

The exact method boundaries must be checked while moving them because
some currently update UI state.

## Important rule

If a current method does both:

``` text
DATA + UI
```

do NOT blindly move the entire method.

Split the responsibility internally:

``` text
DataFetch:
    obtain/return data

StudySync/UI:
    display that data
```

Example:

``` text
OLD:

loadSubjects()
    scan folders
    update subject list
    repaint UI

NEW:

DataFetch:
    getSubjects(folder)

StudySync:
    subjects = dataFetch.getSubjects(folder)

UI:
    displaySubjects(subjects)
```

This is the most important seam in the refactor.

------------------------------------------------------------------------

# 6. Phase 2: Connect `StudySync` to `DataFetch`

After `DataFetch.java` exists:

``` text
StudySync
    |
    +--> DataFetch
```

Add one field:

``` java
private final DataFetch dataFetch = new DataFetch();
```

Do not remove old logic yet.

Replace one data operation at a time.

Recommended order:

1.  Folder selection
2.  Recursive file scanning
3.  Subject discovery
4.  Resource loading
5.  Category/type filtering
6.  File opening

After each replacement:

``` bash
javac -cp "lib/*" -d antigravity-build src/*.java
```

Then run the application.

------------------------------------------------------------------------

# 7. Phase 3: Verify DataFetch Isolation

At this point, `DataFetch.java` should know about:

``` text
Files
Folders
Resources
Resource categories
```

It should not know about:

``` text
JFrame
JPanel
JButton
JTextField
JOptionPane
Ollama
AIRequest
AIResponse
DocumentModel
```

If DataFetch requires Swing objects to work, stop and move only the
UI-specific portion back to StudySync/UI.

The desired dependency is:

``` text
StudySync ---> DataFetch
```

not:

``` text
DataFetch ---> StudySync
```

and not:

``` text
DataFetch <--> UI
```

------------------------------------------------------------------------

# 8. Phase 4: Create `UI.java`

Only after DataFetch is stable should the UI extraction begin.

The current StudySync UI-related methods include:

``` text
applyDarkTheme()
createModernButton()
createModernTextField()
createModernComboBox()
createModernScrollPane()
createSectionBorder()
RoundedBorder
ModernListCellRenderer
createAiPanel()
updateAiSelectionUi()
clearAiSelection()
addResourceCard()
createFileTypeBadge()
buildChatHtmlHead()
appendChatMessage()
markdownToHtml()
isTableSeparator()
closeList()
inlineMarkdown()
escapeHtml()
formatSize()
```

Additional Swing-specific blocks inside other methods should also be
extracted carefully.

------------------------------------------------------------------------

# 9. UI Extraction Strategy

`UI.java` should initially be a helper/facade used by `StudySync`.

Example relationship:

``` text
StudySync
    |
    +--> UI
```

The goal is NOT to make UI.java independent of everything immediately.

Instead, it should provide reusable methods such as:

``` text
createButton(...)
createTextField(...)
createScrollPane(...)
createAIPanel(...)
createResourceCard(...)
createFileTypeBadge(...)
formatChatResponse(...)
```

If a UI method needs data, pass the data into it.

Avoid letting UI.java directly scan files or call Ollama.

------------------------------------------------------------------------

# 10. Handling the Hardest Part: Existing Inner Classes

The current `StudySync.java` contains Swing anonymous/inner classes,
such as:

``` text
paintComponent(...)
mouseEntered(...)
mouseExited(...)
insertUpdate(...)
removeUpdate(...)
changedUpdate(...)
getListCellRendererComponent(...)
SwingWorker callbacks
```

These must NOT be mechanically copied into UI.java.

For every such block ask:

``` text
Does this code only draw or update UI?
        |
       YES
        |
        v
      UI.java

Does this code perform application logic?
        |
       YES
        |
        v
      StudySync.java
```

For example:

``` text
Button click
    |
    +--> UI creates button
    |
    +--> StudySync handles action
```

This keeps event ownership clear.

------------------------------------------------------------------------

# 11. AI Logic Must Stay in StudySync

Do NOT create an `AI.java`.

The user explicitly wants only three application-level parts.

The existing AI architecture remains:

``` text
StudySync
    |
    v
AIService
    |
    v
LocalAI
    |
    v
Ollama
    |
    v
Qwen3.5:4B
```

Keep these methods in StudySync:

``` text
askSelectedFilesWithAi()
askLocalAI()
askLocalAIWithDocuments()
shouldUseVisualContext()
collectAIImages()
showMultiFileAiDialog()
buildConversationInstruction()
```

Reason:

The AI workflow is currently tightly connected to: - selected
resources - document processing - cached documents - conversation
state - UI dialog state

Moving these prematurely creates unnecessary coupling.

------------------------------------------------------------------------

# 12. Document Processing Must Remain Untouched

Do not move any of these into DataFetch or UI:

``` text
DocumentService
DocumentProcessor
DocumentProcessorFactory
PDFProcessor
PPTXProcessor
DOCXProcessor
DocumentModel
DocumentImage
DocumentContextBuilder
```

The flow remains:

``` text
StudySync
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
```

Then:

``` text
DocumentModel
    |
    v
DocumentContextBuilder
    |
    v
AIRequest
    |
    v
AIService
```

This is already working and therefore should be treated as a stable
subsystem.

------------------------------------------------------------------------

# 13. Preserve the Existing AI Performance Optimization

The recent optimization must survive the refactor.

The AI dialog currently avoids repeatedly processing the same resources
during follow-up questions.

Preserve this behavior:

``` text
Initial question
    |
    v
Process documents ONCE
    |
    v
Cache DocumentModels / visuals
    |
    v
Generate answer

Follow-up
    |
    v
Reuse cached documents
    |
    v
Generate answer
```

Do NOT accidentally turn it back into:

``` text
Follow-up
    |
    v
Reparse PDF
    |
    v
Rerender PDF pages
    |
    v
Ask Qwen
```

That would be a regression.

------------------------------------------------------------------------

# 14. Exact Dependency Direction

The final dependency direction should be:

``` text
                 StudySync
                /         \
               v           v
         DataFetch         UI
               |
               v
        Resource / Files

StudySync
    |
    +--> DocumentService
    |
    +--> DocumentContextBuilder
    |
    +--> AIService
```

And:

``` text
AIService <|-- LocalAI

DocumentProcessor <|-- PDFProcessor
DocumentProcessor <|-- PPTXProcessor
DocumentProcessor <|-- DOCXProcessor
```

Avoid circular dependencies such as:

``` text
UI --> StudySync --> UI
```

or:

``` text
DataFetch --> UI --> DataFetch
```

------------------------------------------------------------------------

# 15. Phase 5: Remove Duplicated Original Methods

Only after the new classes are working:

1.  Confirm the new implementation is being called.
2.  Search for remaining calls to the old methods.
3.  Remove the old method.
4.  Compile.
5.  Run.
6.  Test the affected feature.

Never delete an old method merely because a new method exists.

First prove that nothing calls it.

------------------------------------------------------------------------

# 16. Phase 6: Full Regression Test

After all extraction is complete, test the complete application.

## Resource workflow

``` text
Launch
  ↓
Select OneDrive Folder
  ↓
Subjects appear
  ↓
Select Subject
  ↓
Resources appear
  ↓
Filter
  ↓
Open Resource
```

## AI workflow

``` text
Select Resource
  ↓
Ask AI
  ↓
Document Processing
  ↓
Semantic Context
  ↓
Qwen
  ↓
Response
```

## Multimodal workflow

``` text
Select visual document
  ↓
Ask visual question
  ↓
Visual extraction
  ↓
Images sent to Qwen
  ↓
Visual answer
```

## Follow-up workflow

``` text
Initial question
  ↓
Answer
  ↓
Follow-up
  ↓
Cached documents reused
  ↓
Answer
```

## Copy workflow

``` text
AI response
  ↓
Copy
  ↓
Clipboard contains latest response only
```

------------------------------------------------------------------------

# 17. Final File Structure

The intended final application-level structure is exactly:

``` text
src/
│
├── StudySync.java
├── DataFetch.java
├── UI.java
│
├── AIRequest.java
├── AIResponse.java
├── AIService.java
├── LocalAI.java
│
├── DocumentModel.java
├── DocumentImage.java
├── DocumentProcessor.java
├── DocumentProcessorFactory.java
├── DocumentService.java
├── DocumentContextBuilder.java
├── PDFProcessor.java
├── PPTXProcessor.java
├── DOCXProcessor.java
│
└── ResourceCluster.java
```

No `Main.java`.

No `StudySyncApp.java`.

No extra controller class.

The three requested application-level components are:

``` text
StudySync.java
DataFetch.java
UI.java
```

------------------------------------------------------------------------

# 18. Mapping to Software Engineering Documentation

This structure gives a clean basis for the submission.

## User Stories

Primary actor:

``` text
Student
```

Stories can map to:

``` text
UI
 |
 +--> Browse subjects
 +--> View resources
 +--> Filter resources
 +--> Select resources
 +--> Ask AI
 +--> Ask follow-up
 +--> Copy response
```

## Use Case Diagram

Actor:

``` text
Student
```

Use cases:

``` text
Browse Subjects
View Resources
Filter Resources
Open Resource
Select Resources
Ask StudySync AI
View AI Response
Ask Follow-up Question
Copy AI Response
Refresh Resources
```

## Activity Diagrams

Major workflows:

### Resource workflow

``` text
Select Folder
    ↓
Fetch Files
    ↓
Identify Subjects
    ↓
Display Resources
    ↓
Filter / Search
    ↓
Open Resource
```

### AI workflow

``` text
Select Resources
    ↓
Enter Question
    ↓
Validate Selection
    ↓
Process Documents
    ↓
Build Context
    ↓
Select Visual Context if Required
    ↓
Send to Local AI
    ↓
Display Response
    ↓
Follow-up?
```

## Class Diagram

Primary application classes:

``` text
StudySync
DataFetch
UI
```

Supporting classes:

``` text
ResourceCluster
DocumentService
DocumentProcessor
PDFProcessor
PPTXProcessor
DOCXProcessor
DocumentModel
DocumentImage
DocumentContextBuilder
AIService
LocalAI
AIRequest
AIResponse
```

## Sequence Diagrams

Recommended:

1.  Browse Resources
2.  Filter Resources
3.  Open Resource
4.  Ask StudySync AI
5.  Ask Follow-up Question

## State Transition Diagram

Use the AI conversation/resource-selection lifecycle:

``` text
IDLE
  ↓
RESOURCE SELECTED
  ↓
PROCESSING
  ↓
ANSWERED
  ↓
FOLLOW-UP
  ↓
PROCESSING
```

## System Architecture

Three major application components:

``` text
UI
 |
 v
StudySync
 |
 +------> DataFetch
 |
 +------> Document Processing
 |
 +------> AIService
             |
             v
           LocalAI
             |
             v
          Ollama
             |
             v
         Qwen3.5
```

------------------------------------------------------------------------

# 19. Golden Rules During Refactoring

### Rule 1

**Do not change working behavior.**

### Rule 2

**Do not modify the document-processing subsystem unless compilation
forces a compatibility change.**

### Rule 3

**Do not modify LocalAI/Ollama behavior.**

### Rule 4

**Do not remove the caching optimization.**

### Rule 5

**Do not create additional application-level classes.**

### Rule 6

**Compile after every extraction.**

### Rule 7

**Run the application after every major extraction.**

### Rule 8

**Never copy-paste logic into the new class while leaving two competing
implementations alive.**

### Rule 9

**Move data logic to DataFetch, presentation logic to UI,
orchestration/AI/application logic stays in StudySync.**

### Rule 10

**The final diagrams must describe the real code.**

------------------------------------------------------------------------

# 20. Definition of Done

The refactor is complete only when all of the following are true:

-   [ ] `StudySync.java` remains functional.
-   [ ] `DataFetch.java` handles resource/file acquisition.
-   [ ] `UI.java` handles presentation helpers/components.
-   [ ] No duplicate data-fetching implementation remains.
-   [ ] No duplicate UI implementation remains.
-   [ ] AI functionality remains unchanged.
-   [ ] PDF processing remains unchanged.
-   [ ] DOCX processing remains unchanged.
-   [ ] PPTX processing remains unchanged.
-   [ ] Visual extraction remains functional.
-   [ ] Follow-up caching remains functional.
-   [ ] Copy button remains functional.
-   [ ] Resource classification remains functional.
-   [ ] Search/filter remains functional.
-   [ ] OneDrive folder selection remains functional.
-   [ ] Clean compilation succeeds.
-   [ ] Application launches successfully.
-   [ ] All major workflows pass regression testing.

Final compilation:

``` bash
rm -rf antigravity-build
mkdir -p antigravity-build
javac -cp "lib/*" -d antigravity-build src/*.java
```

Final execution:

``` bash
java -cp "antigravity-build:lib/*" StudySync
```

------------------------------------------------------------------------

# Final Refactoring Principle

This is a **disintegration, not a redesign**.

The working StudySync system is the source of truth.

We are simply exposing three clean application-level responsibilities:

``` text
                 STUDYSYNC
                     |
        +------------+------------+
        |                         |
        v                         v
    DATAFETCH                    UI
        |                         |
        +------------+------------+
                     |
                     v
                  STUDYSYNC
                     |
             +-------+-------+
             |               |
             v               v
        DOCUMENTS           AI
             |               |
             v               v
      PDF/DOCX/PPTX       LocalAI
                             |
                             v
                           Qwen
```

**Nothing else gets unnecessarily disturbed.**
