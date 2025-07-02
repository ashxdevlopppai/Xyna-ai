# Javris - The Ultimate AI Assistant for Android

## Overview
Javris is a fully autonomous, AI-powered personal assistant for Android that monitors, analyzes, and automates your digital life. It runs entirely on your device using Android Studio with Python integration via Chaquopy, and connects to the internet only when needed to access AI capabilities through the OpenRouter API.

## Features
- **AI Core Functionalities**
  - Natural language processing using Qwen-VL-Plus
  - Voice and text interactions
  - Multi-language support
  - Custom wake words
  - Chain-of-thought reasoning

- **Daily Monitoring & Personal Assistance**
  - Activity tracking
  - Daily summary reports
  - Smart to-do lists and reminders
  - Calendar integration

- **Chat & Relationship Monitoring**
  - Sentiment analysis
  - Communication insights
  - Smart responses

- **Mobile Automation & System Control**
  - Voice commands
  - Cross-app automation
  - Web browsing assistance
  - Smart notifications

- **Additional Features**
  - AR guidance and HUD
  - IoT integration
  - Local data storage with cloud backup
  - Customizable AI personality

## Setup Requirements
- Android Studio Arctic Fox or newer
- Minimum Android SDK: API 26 (Android 8.0)
- Chaquopy plugin for Python integration
- OpenRouter API key
- Google Sign-In configuration

## Installation
1. Clone this repository
2. Open the project in Android Studio
3. Configure your OpenRouter API key in `local.properties`
4. Sync Gradle and build the project

## Configuration
Create a `local.properties` file in the project root and add:
```properties
openrouter.api.key=your_api_key_here
```

## Contributing
Contributions are welcome! Please read our contributing guidelines and submit pull requests.

## License
This project is licensed under the MIT License - see the LICENSE file for details.

## Security
- All data is encrypted and stored locally
- Cloud backups are encrypted
- API keys are securely stored
- User consent required for sensitive features

## Support
For support, please open an issue in the GitHub repository or contact the development team. 