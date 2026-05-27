import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'app_colors.dart';

abstract final class AppTextStyles {
  static TextStyle get displayLarge => GoogleFonts.inter(
    fontSize: 32, fontWeight: FontWeight.bold, color: AppColors.foreground,
  );
  static TextStyle get displayMedium => GoogleFonts.inter(
    fontSize: 24, fontWeight: FontWeight.bold, color: AppColors.foreground,
  );
  static TextStyle get headlineLarge => GoogleFonts.inter(
    fontSize: 20, fontWeight: FontWeight.w600, color: AppColors.foreground,
  );
  static TextStyle get headlineMedium => GoogleFonts.inter(
    fontSize: 16, fontWeight: FontWeight.w600, color: AppColors.foreground,
  );
  static TextStyle get bodyLarge => GoogleFonts.inter(
    fontSize: 16, color: AppColors.foreground,
  );
  static TextStyle get bodyMedium => GoogleFonts.inter(
    fontSize: 14, color: AppColors.foreground,
  );
  static TextStyle get bodySmall => GoogleFonts.inter(
    fontSize: 12, color: AppColors.mutedFg,
  );
  static TextStyle get labelLarge => GoogleFonts.inter(
    fontSize: 14, fontWeight: FontWeight.w500, color: AppColors.foreground,
  );
  static TextStyle get labelSmall => GoogleFonts.inter(
    fontSize: 11, fontWeight: FontWeight.w500,
    color: AppColors.mutedFg, letterSpacing: 0.5,
  );
  static TextStyle get titleLarge => GoogleFonts.inter(
    fontSize: 18, fontWeight: FontWeight.w600, color: AppColors.foreground,
  );
  static TextStyle get titleMedium => GoogleFonts.inter(
    fontSize: 16, fontWeight: FontWeight.w600, color: AppColors.foreground,
  );
  static TextStyle get titleSmall => GoogleFonts.inter(
    fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.foreground,
  );
}
