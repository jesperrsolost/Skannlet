# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project uses Semantic Versioning.

## [1.1.0] - 2026-08-12

### Added

- Direct TCP label printing to TSC TC200 printers using TSPL.
- Printer configuration from the profile, including IPv4 address, port, connection testing, and printer-status feedback.
- Built-in `Small Barcode` format for 35 × 14 mm labels.
- Custom label formats with configurable dimensions, media tracking, barcode height, and module width.
- Support for gap, black-mark, and continuous label media.
- Per-row label printing from project details with progress and error feedback.
- Persistent local printer settings and custom label formats.
- Unit tests for printer settings, TCP communication, TSPL rendering, printer management, and label-format input.

### Changed

- Reorganized the profile feature into separate route, screen, component, dialog, About, and printer packages.
- Moved collection sharing, PDF rendering, print HTML, and print models into the export package.
- Moved scanner input into the scan UI feature package.
- Improved the About page for small screens and centralized version, license, and source-code metadata.
- Made destructive confirmation actions visually consistent.
- Replaced template tests and unused project files with feature-focused tests and production structure.

### Fixed

- Preserve a label format's content layout when copying or editing it.
- Scope radio-group accessibility semantics to label-format choices.
- Show active-user status as passive content instead of a non-functional button.
- Keep printer settings out of Android backup and device-transfer data.

## [1.0.1] - 2026-07-03

### Fixed

- Text wrapping for long titles in PDFs exported to mail.
- Cleaned up PDF exporting.
- Minor bug fixes.

### Changed

- Scanned items set to quantity delivered instead of quantity ordered.

## [1.0.0] - 2026-06-29

### Added

- Initial release.

[1.1.0]: https://github.com/jesperrsolost/Skannlet/compare/v1.0...v1.1.0
[1.0.1]: https://github.com/jesperrsolost/Skannlet/commit/05e9337
[1.0.0]: https://github.com/jesperrsolost/Skannlet/releases/tag/v1.0
