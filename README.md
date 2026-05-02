[![Latest release](https://img.shields.io/github/v/release/alfahrelrifananda/threshold?style=for-the-badge)](https://github.com/alfahrelrifananda/threshold/releases)
[![Flutter](https://img.shields.io/badge/Flutter-02569B?style=for-the-badge&logo=flutter&logoColor=white)](https://flutter.dev)
[![License](https://img.shields.io/github/license/alfahrelrifananda/threshold?style=for-the-badge)](LICENSE)

# Threshold

A screen time management app built with Flutter. Track app usage, set timers, and manage your digital habits.

[![Get it on GitHub](https://img.shields.io/badge/Get%20it%20on-GitHub-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/alfahrelrifananda/threshold/releases)

## Features

- Real-time app usage tracking with session details
- Set daily time limits (5-300 minutes) for apps
- Home screen widgets (1x1 and 2x2 sizes)
- Charts and hourly breakdowns
- Filter out apps you don't want to track
- All data stays on your device, no internet required
- Device Admin protection to prevent uninstallation

## Screenshots

<div style="display: flex; justify-content: space-around; gap: 10px;">
  <img src="./screenshot/ss1.png" width="200">
  <img src="./screenshot/ss2.png" width="200">
  <img src="./screenshot/ss3.png" width="200">
</div>

## Tech Stack

- **Language:** Dart & Kotlin
- **Framework:** Flutter 3.24+
- **UI:** Material Design 3
- **Charts:** fl_chart
- **Platform:** Android (API 24+)
- **Target SDK:** API 36

## Getting Started

### Prerequisites

- [Flutter SDK](https://flutter.dev/docs/get-started/install) (latest stable version)
- Android Studio or VS Code with Flutter extensions
- Android device or emulator (API 24+)

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/alfahrelrifananda/threshold.git
   cd threshold
   ```

2. Install dependencies:
   ```bash
   flutter pub get
   ```

3. Run the app:
   ```bash
   flutter run
   ```

### Build APK

```bash
# Debug APK
flutter build apk

# Release APK
flutter build apk --release

# Split APKs by architecture
flutter build apk --split-per-abi
```

The APK will be in `build/app/outputs/flutter-apk/`

## Required Permissions

The app requires these permissions:

1. **Usage Access** – Track app usage statistics
2. **Accessibility Service** – Monitor app activity and enforce timers
3. **Display Over Other Apps** – Show timer notifications
4. **Device Administrator** – Prevent unauthorized app removal

All permissions are requested on first launch with explanations.

## Contributing

If you'd like to contribute:
- Fork the project
- Open issues for bugs or feature requests
- Submit pull requests
- Follow Flutter/Dart style guidelines

## License

This project is licensed under the **GNU General Public License v3.0**. See the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Built with [Flutter](https://flutter.dev)
- Charts by [fl_chart](https://pub.dev/packages/fl_chart)
- Inspired by Digital Wellbeing
