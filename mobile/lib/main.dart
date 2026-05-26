import 'main_dev.dart' as entry;

// Punto de entrada por defecto → flavor dev.
// En CI/CD usar:
//   flutter run --flavor dev -t lib/main_dev.dart
//   flutter run --flavor staging -t lib/main_staging.dart
//   flutter run --flavor prod -t lib/main_prod.dart
void main() => entry.main();
