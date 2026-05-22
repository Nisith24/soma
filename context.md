# MCQ Study & Quiz App

## Core Functionality
The application is a channelized learning platform designed to help users master diverse subjects through a structured Multiple Choice Question (MCQ) framework. It bridges the gap between passive reading and active testing.

### 1. Subject & Topic Organization
- **Categorization**: Questions are organized by **Subject** (e.g., Computer Science, General Medicine, World History) and further subdivided by **Topic** (e.g., Algorithms, Cardiology, Ancient Civilizations).
- **Discovery**: A clean, dashboard-style interface allows users to explore categories and track the number of questions available in each niche.

### 2. Versatile Learning Modes
- **List Mode**: Displays a scrollable feed of questions. This mode is optimized for rapid review and "pre-reading" before attempting a quiz.
- **Quiz Mode**: Transition into a focused, one-question-at-a-time interactive experience.
    - **Progress Tracking**: A top-level progress bar indicates the current position in the question set.
    - **Instant Feedback**: Users receive immediate visual cues upon selecting an option (Teal for correct, Red for incorrect).
    - **Deep Dive Explanations**: Once a question is answered, a detailed explanation block reveals the logic behind the correct answer to reinforce learning.

### 3. Data & Persistence
- **JSON Engine**: The app architecture is centered around a robust JSON processing engine. Users can import or load MCQ sets via a standardized schema (`McqField`).
- **Local Cache**: Questions and user progress are persisted locally in `persisted_mcq.json`, ensuring the study session remains uninterrupted across app restarts.

### 4. User Experience & Design
- **Single-Tap Navigation**: A streamlined top-bar toggle allows for instant switching between study (List) and test (Quiz) environments.
- **Modern UI**: Adheres to Material 3 principles with generous padding, clear typography hierarchy, and adaptive layouts for high-readability on mobile devices.
- **Accessibility**: Includes high-contrast feedback states and touch-friendly interactive surfaces (min 48dp).

## Technical Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Modern, Declarative)
- **Architecture**: MVVM (Model-View-ViewModel) with StateFlow for reactive UI updates.
- **Serialization**: Kotlinx.serialization for efficient JSON parsing.
