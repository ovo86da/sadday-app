import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/api/app_exception.dart';
import '../../../../core/api/auth_dio_provider.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../docs_legales/data/docs_legales_remote_data_source.dart';
import '../../../docs_legales/domain/models/docs_legales_models.dart';
import '../../data/auth_remote_data_source.dart';

// ─── Wizard state ─────────────────────────────────────────────────────────────

class _WizardData {
  _WizardData({
    this.step1DocIds = const [],
    this.nombre = '',
    this.apellido = '',
    this.fechaNacimiento = '',
    this.direccion = '',
    this.username = '',
    this.password = '',
    this.contact1,
    this.contact2,
    this.medical,
    this.step5DocIds = const [],
  });

  final List<String> step1DocIds;
  final String nombre;
  final String apellido;
  final String fechaNacimiento;
  final String direccion;
  final String username;
  final String password;
  final Map<String, String>? contact1;
  final Map<String, String>? contact2;
  final Map<String, dynamic>? medical;
  final List<String> step5DocIds;

  _WizardData copyWith({
    List<String>? step1DocIds,
    String? nombre,
    String? apellido,
    String? fechaNacimiento,
    String? direccion,
    String? username,
    String? password,
    Map<String, String>? contact1,
    Map<String, String>? contact2,
    Map<String, dynamic>? medical,
    List<String>? step5DocIds,
  }) =>
      _WizardData(
        step1DocIds: step1DocIds ?? this.step1DocIds,
        nombre: nombre ?? this.nombre,
        apellido: apellido ?? this.apellido,
        fechaNacimiento: fechaNacimiento ?? this.fechaNacimiento,
        direccion: direccion ?? this.direccion,
        username: username ?? this.username,
        password: password ?? this.password,
        contact1: contact1 ?? this.contact1,
        contact2: contact2 ?? this.contact2,
        medical: medical ?? this.medical,
        step5DocIds: step5DocIds ?? this.step5DocIds,
      );
}

// ─── Main screen ──────────────────────────────────────────────────────────────

class CompleteRegistrationScreen extends ConsumerStatefulWidget {
  const CompleteRegistrationScreen(
      {required this.invitationToken, super.key});
  final String invitationToken;

  @override
  ConsumerState<CompleteRegistrationScreen> createState() =>
      _CompleteRegistrationScreenState();
}

class _CompleteRegistrationScreenState
    extends ConsumerState<CompleteRegistrationScreen> {
  int _step = 1;
  _WizardData _data = _WizardData();
  bool _done = false;
  bool _submitting = false;

  static const int _totalSteps = 5;

  void _nextStep(_WizardData updated) {
    setState(() {
      _data = updated;
      _step++;
    });
  }

  void _prevStep() {
    if (_step > 1) setState(() => _step--);
  }

  Future<void> _submit(_WizardData finalData) async {
    setState(() { _submitting = true; _data = finalData; });
    try {
      final ds = AuthRemoteDataSource(ref.read(authDioProvider));
      final allDocIds = [...finalData.step1DocIds, ...finalData.step5DocIds];
      final contacts = [finalData.contact1, finalData.contact2]
          .where((c) => c != null && (c['nombreCompleto'] ?? '').isNotEmpty)
          .map((c) => c!)
          .toList();
      await ds.completeRegistration(
        invitationToken: widget.invitationToken,
        username: finalData.username,
        nombre: finalData.nombre,
        apellido: finalData.apellido,
        password: finalData.password,
        passwordConfirmation: finalData.password,
        fechaNacimiento: finalData.fechaNacimiento.isNotEmpty
            ? finalData.fechaNacimiento
            : null,
        direccion: finalData.direccion,
        documentIdsToAccept: allDocIds.isNotEmpty ? allDocIds : null,
        contactosEmergencia: contacts.isNotEmpty
            ? contacts.map((c) => {
                  'nombreCompleto': c['nombreCompleto'] ?? '',
                  'relacion': c['relacion'] ?? '',
                  if ((c['celular'] ?? '').isNotEmpty) 'celular': c['celular'],
                  if ((c['direccion'] ?? '').isNotEmpty)
                    'direccion': c['direccion'],
                }).toList()
            : null,
        informacionMedica: finalData.medical,
      );
      setState(() => _done = true);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(unwrapDio(e).toString()),
          backgroundColor: AppColors.destructive,
        ));
      }
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_done) return const _SuccessScreen();

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Completa tu registro'),
        leading: _step > 1
            ? IconButton(
                icon: const Icon(Icons.arrow_back),
                onPressed: _prevStep,
              )
            : null,
      ),
      body: Column(
        children: [
          _StepIndicator(current: _step, total: _totalSteps),
          Expanded(
            child: switch (_step) {
              1 => _Step1Consents(
                  invitationToken: widget.invitationToken,
                  accepted: _data.step1DocIds,
                  onNext: (ids) =>
                      _nextStep(_data.copyWith(step1DocIds: ids)),
                ),
              2 => _Step2PersonalData(
                  data: _data,
                  onNext: (d) => _nextStep(d),
                ),
              3 => _Step3EmergencyContacts(
                  contact1: _data.contact1,
                  contact2: _data.contact2,
                  onNext: (c1, c2) =>
                      _nextStep(_data.copyWith(contact1: c1, contact2: c2)),
                ),
              4 => _Step4MedicalInfo(
                  medical: _data.medical,
                  onNext: (m) => _nextStep(_data.copyWith(medical: m)),
                ),
              5 => _Step5FinalAcceptances(
                  accepted: _data.step5DocIds,
                  submitting: _submitting,
                  onSubmit: (ids) => _submit(_data.copyWith(step5DocIds: ids)),
                ),
              _ => const SizedBox.shrink(),
            },
          ),
        ],
      ),
    );
  }
}

// ─── Step indicator ───────────────────────────────────────────────────────────

class _StepIndicator extends StatelessWidget {
  const _StepIndicator({required this.current, required this.total});
  final int current;
  final int total;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
        child: Row(
          children: List.generate(total, (i) {
            final n = i + 1;
            final done = n < current;
            final active = n == current;
            return Expanded(
              child: Row(
                children: [
                  Expanded(
                    child: Container(
                      height: 4,
                      decoration: BoxDecoration(
                        color: done || active
                            ? AppColors.primary
                            : AppColors.border,
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                  ),
                  if (i < total - 1) const SizedBox(width: 4),
                ],
              ),
            );
          }),
        ),
      );
}

// ─── Step 1: Initial consents ─────────────────────────────────────────────────

class _Step1Consents extends ConsumerStatefulWidget {
  const _Step1Consents({
    required this.invitationToken,
    required this.accepted,
    required this.onNext,
  });
  final String invitationToken;
  final List<String> accepted;
  final void Function(List<String> acceptedIds) onNext;

  @override
  ConsumerState<_Step1Consents> createState() => _Step1ConsentsState();
}

class _Step1ConsentsState extends ConsumerState<_Step1Consents> {
  List<LegalDoc>? _docs;
  bool _loading = true;
  String? _error;
  final Set<String> _checked = {};

  @override
  void initState() {
    super.initState();
    _checked.addAll(widget.accepted);
    _loadDocs();
  }

  Future<void> _loadDocs() async {
    try {
      final ds = DocsLegalesRemoteDataSource(ref.read(authDioProvider));
      final docs = await Future.wait([
        ds.getActiveDocByCode('DATA_PROCESSING_POLICY'),
        ds.getActiveDocByCode('MEDICAL_DATA_CONSENT'),
      ]);
      if (mounted) {
        setState(() {
          _docs = docs;
          _loading = false;
        });
      }
    } catch (_) {
      if (mounted) setState(() { _loading = false; _error = null; });
      // Si los documentos no están disponibles, permitir continuar sin aceptar
    }
  }

  @override
  Widget build(BuildContext context) {
    final allChecked = _docs == null ||
        _docs!.isEmpty ||
        _docs!.every((d) => _checked.contains(d.id));

    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('Consentimientos iniciales',
              style: AppTextStyles.headlineMedium),
          const SizedBox(height: 8),
          Text(
            'Para continuar, debes leer y aceptar los siguientes documentos.',
            style: AppTextStyles.bodyMedium.copyWith(color: AppColors.mutedFg),
          ),
          const SizedBox(height: 24),
          if (_loading)
            const Center(child: CircularProgressIndicator())
          else if (_error != null)
            Text(_error!,
                style: const TextStyle(color: AppColors.destructive))
          else if (_docs == null || _docs!.isEmpty)
            Text(
              'No hay documentos obligatorios en este momento.',
              style:
                  AppTextStyles.bodyMedium.copyWith(color: AppColors.mutedFg),
            )
          else
            ..._docs!.map((doc) => _DocCheckTile(
                  doc: doc,
                  checked: _checked.contains(doc.id),
                  onToggle: (v) => setState(() {
                    if (v) {
                      _checked.add(doc.id);
                    } else {
                      _checked.remove(doc.id);
                    }
                  }),
                )),
          const SizedBox(height: 32),
          AppButton(
            label: 'Continuar',
            onPressed: allChecked
                ? () => widget.onNext(_checked.toList())
                : null,
          ),
        ],
      ),
    );
  }
}

class _DocCheckTile extends StatelessWidget {
  const _DocCheckTile(
      {required this.doc, required this.checked, required this.onToggle});
  final LegalDoc doc;
  final bool checked;
  final void Function(bool) onToggle;

  @override
  Widget build(BuildContext context) => Container(
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: checked
              ? AppColors.primary.withValues(alpha: 0.08)
              : AppColors.secondary,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: checked ? AppColors.primary : AppColors.border,
          ),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Checkbox(
              value: checked,
              activeColor: AppColors.primary,
              onChanged: (v) => onToggle(v ?? false),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(doc.title,
                      style: AppTextStyles.bodyMedium
                          .copyWith(fontWeight: FontWeight.w600)),
                  const SizedBox(height: 4),
                  Text('v${doc.version}',
                      style: AppTextStyles.labelSmall
                          .copyWith(color: AppColors.mutedFg)),
                ],
              ),
            ),
          ],
        ),
      );
}

// ─── Step 2: Personal data ────────────────────────────────────────────────────

class _Step2PersonalData extends StatefulWidget {
  const _Step2PersonalData({required this.data, required this.onNext});
  final _WizardData data;
  final void Function(_WizardData) onNext;

  @override
  State<_Step2PersonalData> createState() => _Step2PersonalDataState();
}

class _Step2PersonalDataState extends State<_Step2PersonalData> {
  late final _nombre = TextEditingController(text: widget.data.nombre);
  late final _apellido = TextEditingController(text: widget.data.apellido);
  late final _fechaNac =
      TextEditingController(text: widget.data.fechaNacimiento);
  late final _direccion = TextEditingController(text: widget.data.direccion);
  late final _username = TextEditingController(text: widget.data.username);
  late final _password = TextEditingController(text: widget.data.password);
  late final _confirm = TextEditingController(text: widget.data.password);
  bool _obscure1 = true;
  bool _obscure2 = true;
  String? _error;

  @override
  void dispose() {
    for (final c in [
      _nombre, _apellido, _fechaNac, _direccion, _username, _password, _confirm
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: DateTime(2000),
      firstDate: DateTime(1920),
      lastDate: DateTime.now().subtract(const Duration(days: 365 * 10)),
      builder: (context, child) => Theme(
        data: Theme.of(context).copyWith(
          colorScheme: ColorScheme.dark(
            primary: AppColors.primary,
            onPrimary: AppColors.background,
            surface: AppColors.sidebar,
          ),
        ),
        child: child!,
      ),
    );
    if (picked != null) {
      _fechaNac.text =
          '${picked.year}-${picked.month.toString().padLeft(2, '0')}-${picked.day.toString().padLeft(2, '0')}';
    }
  }

  void _validate() {
    setState(() => _error = null);
    if (_nombre.text.trim().isEmpty || _apellido.text.trim().isEmpty) {
      setState(() => _error = 'Nombre y apellido son obligatorios');
      return;
    }
    if (_username.text.trim().length < 4) {
      setState(() => _error = 'El usuario debe tener al menos 4 caracteres');
      return;
    }
    if (!RegExp(r'^[a-z0-9._-]+$').hasMatch(_username.text.trim())) {
      setState(
          () => _error = 'El usuario solo puede tener letras, números, . - _');
      return;
    }
    if (_password.text.length < 12) {
      setState(() => _error = 'La contraseña debe tener al menos 12 caracteres');
      return;
    }
    if (_password.text != _confirm.text) {
      setState(() => _error = 'Las contraseñas no coinciden');
      return;
    }
    widget.onNext(widget.data.copyWith(
      nombre: _nombre.text.trim(),
      apellido: _apellido.text.trim(),
      fechaNacimiento: _fechaNac.text,
      direccion: _direccion.text.trim(),
      username: _username.text.trim(),
      password: _password.text,
    ));
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('Datos personales', style: AppTextStyles.headlineMedium),
          const SizedBox(height: 24),
          _field('Nombre *', _nombre,
              textCapitalization: TextCapitalization.words),
          const SizedBox(height: 12),
          _field('Apellido *', _apellido,
              textCapitalization: TextCapitalization.words),
          const SizedBox(height: 12),
          GestureDetector(
            onTap: _pickDate,
            child: AbsorbPointer(
              child: _field('Fecha de nacimiento',
                  _fechaNac, hint: 'YYYY-MM-DD'),
            ),
          ),
          const SizedBox(height: 12),
          _field('Dirección', _direccion),
          const SizedBox(height: 12),
          _field('Usuario *', _username,
              hint: 'ej. juan.perez',
              autocorrect: false),
          const SizedBox(height: 12),
          TextField(
            controller: _password,
            obscureText: _obscure1,
            decoration: InputDecoration(
              labelText: 'Contraseña *',
              suffixIcon: IconButton(
                icon: Icon(_obscure1
                    ? Icons.visibility_off
                    : Icons.visibility),
                onPressed: () =>
                    setState(() => _obscure1 = !_obscure1),
              ),
            ),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _confirm,
            obscureText: _obscure2,
            decoration: InputDecoration(
              labelText: 'Confirmar contraseña *',
              suffixIcon: IconButton(
                icon: Icon(_obscure2
                    ? Icons.visibility_off
                    : Icons.visibility),
                onPressed: () =>
                    setState(() => _obscure2 = !_obscure2),
              ),
            ),
          ),
          if (_error != null) ...[
            const SizedBox(height: 12),
            Text(_error!,
                style: const TextStyle(color: AppColors.destructive)),
          ],
          const SizedBox(height: 32),
          AppButton(label: 'Continuar', onPressed: _validate),
        ],
      ),
    );
  }

  Widget _field(String label, TextEditingController ctrl,
      {String? hint,
      TextInputType? keyboardType,
      TextCapitalization textCapitalization = TextCapitalization.none,
      bool autocorrect = true}) =>
      TextField(
        controller: ctrl,
        keyboardType: keyboardType,
        textCapitalization: textCapitalization,
        autocorrect: autocorrect,
        decoration: InputDecoration(labelText: label, hintText: hint),
      );
}

// ─── Step 3: Emergency contacts ───────────────────────────────────────────────

const _relacionOptions = [
  'Cónyuge/Pareja', 'Madre', 'Padre', 'Hijo/a', 'Hermano/a',
  'Abuelo/a', 'Tío/a', 'Primo/a', 'Amigo/a', 'Otro',
];

class _Step3EmergencyContacts extends StatefulWidget {
  const _Step3EmergencyContacts(
      {required this.contact1, required this.contact2, required this.onNext});
  final Map<String, String>? contact1;
  final Map<String, String>? contact2;
  final void Function(Map<String, String>, Map<String, String>) onNext;

  @override
  State<_Step3EmergencyContacts> createState() =>
      _Step3EmergencyContactsState();
}

class _Step3EmergencyContactsState extends State<_Step3EmergencyContacts> {
  late final _c1Nombre =
      TextEditingController(text: widget.contact1?['nombreCompleto'] ?? '');
  late final _c1Celular =
      TextEditingController(text: widget.contact1?['celular'] ?? '');
  late final _c1Direccion =
      TextEditingController(text: widget.contact1?['direccion'] ?? '');
  late final _c2Nombre =
      TextEditingController(text: widget.contact2?['nombreCompleto'] ?? '');
  late final _c2Celular =
      TextEditingController(text: widget.contact2?['celular'] ?? '');
  late final _c2Direccion =
      TextEditingController(text: widget.contact2?['direccion'] ?? '');

  String? _c1Relacion;
  String? _c2Relacion;
  String? _error;

  @override
  void initState() {
    super.initState();
    _c1Relacion = widget.contact1?['relacion'];
    _c2Relacion = widget.contact2?['relacion'];
  }

  @override
  void dispose() {
    for (final c in [
      _c1Nombre, _c1Celular, _c1Direccion,
      _c2Nombre, _c2Celular, _c2Direccion
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  void _validate() {
    if (_c1Nombre.text.trim().isEmpty || _c1Relacion == null) {
      setState(
          () => _error = 'El contacto 1 requiere nombre y relación');
      return;
    }
    if (_c2Nombre.text.trim().isEmpty || _c2Relacion == null) {
      setState(
          () => _error = 'El contacto 2 requiere nombre y relación');
      return;
    }
    setState(() => _error = null);
    widget.onNext(
      {
        'nombreCompleto': _c1Nombre.text.trim(),
        'relacion': _c1Relacion!,
        'celular': _c1Celular.text.trim(),
        'direccion': _c1Direccion.text.trim(),
      },
      {
        'nombreCompleto': _c2Nombre.text.trim(),
        'relacion': _c2Relacion!,
        'celular': _c2Celular.text.trim(),
        'direccion': _c2Direccion.text.trim(),
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('Contactos de emergencia', style: AppTextStyles.headlineMedium),
          const SizedBox(height: 8),
          Text('Se requieren 2 contactos.',
              style:
                  AppTextStyles.bodyMedium.copyWith(color: AppColors.mutedFg)),
          const SizedBox(height: 24),
          _contactSection('Contacto 1', _c1Nombre, _c1Celular, _c1Direccion,
              _c1Relacion, (v) => setState(() => _c1Relacion = v)),
          const SizedBox(height: 24),
          _contactSection('Contacto 2', _c2Nombre, _c2Celular, _c2Direccion,
              _c2Relacion, (v) => setState(() => _c2Relacion = v)),
          if (_error != null) ...[
            const SizedBox(height: 12),
            Text(_error!,
                style: const TextStyle(color: AppColors.destructive)),
          ],
          const SizedBox(height: 32),
          AppButton(label: 'Continuar', onPressed: _validate),
        ],
      ),
    );
  }

  Widget _contactSection(
    String title,
    TextEditingController nombre,
    TextEditingController celular,
    TextEditingController direccion,
    String? relacion,
    void Function(String?) onRelacionChanged,
  ) =>
      Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title,
              style: AppTextStyles.titleMedium
                  .copyWith(fontWeight: FontWeight.w600)),
          const SizedBox(height: 10),
          TextField(
              controller: nombre,
              textCapitalization: TextCapitalization.words,
              decoration:
                  const InputDecoration(labelText: 'Nombre completo *')),
          const SizedBox(height: 10),
          _RelacionPicker(
              value: relacion, onChanged: onRelacionChanged),
          const SizedBox(height: 10),
          TextField(
              controller: celular,
              keyboardType: TextInputType.phone,
              decoration: const InputDecoration(labelText: 'Celular')),
          const SizedBox(height: 10),
          TextField(
              controller: direccion,
              decoration: const InputDecoration(labelText: 'Dirección')),
        ],
      );
}

class _RelacionPicker extends StatelessWidget {
  const _RelacionPicker({required this.value, required this.onChanged});
  final String? value;
  final void Function(String?) onChanged;

  @override
  Widget build(BuildContext context) => GestureDetector(
        onTap: () async {
          final sel = await showModalBottomSheet<String>(
            context: context,
            backgroundColor: AppColors.background,
            shape: const RoundedRectangleBorder(
              borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
            ),
            builder: (_) => ListView(
              shrinkWrap: true,
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
              children: [
                Center(
                  child: Container(
                    width: 36,
                    height: 4,
                    decoration: BoxDecoration(
                      color: AppColors.border,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                const Text('Relación'),
                const SizedBox(height: 8),
                ..._relacionOptions.map((o) => ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(o),
                      trailing: o == value
                          ? const Icon(Icons.check, color: AppColors.primary)
                          : null,
                      onTap: () => Navigator.pop(context, o),
                    )),
              ],
            ),
          );
          if (sel != null) onChanged(sel);
        },
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
          decoration: BoxDecoration(
            color: AppColors.secondary,
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: AppColors.border),
          ),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Relación *',
                        style: AppTextStyles.labelSmall
                            .copyWith(color: AppColors.mutedFg)),
                    const SizedBox(height: 2),
                    Text(
                      value ?? 'Seleccionar',
                      style: AppTextStyles.bodyMedium.copyWith(
                        color: value != null
                            ? AppColors.foreground
                            : AppColors.mutedFg,
                      ),
                    ),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right,
                  size: 18, color: AppColors.mutedFg),
            ],
          ),
        ),
      );
}

// ─── Step 4: Medical info ─────────────────────────────────────────────────────

const _bloodTypes = [
  'A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'
];

class _Step4MedicalInfo extends StatefulWidget {
  const _Step4MedicalInfo({required this.medical, required this.onNext});
  final Map<String, dynamic>? medical;
  final void Function(Map<String, dynamic>?) onNext;

  @override
  State<_Step4MedicalInfo> createState() => _Step4MedicalInfoState();
}

class _Step4MedicalInfoState extends State<_Step4MedicalInfo> {
  String? _bloodType;
  bool _alergias = false;
  final _alergiasDetail = TextEditingController();
  bool _condicion = false;
  final _condicionDetail = TextEditingController();
  bool _medicacion = false;
  final _medicacionDetail = TextEditingController();

  @override
  void initState() {
    super.initState();
    final m = widget.medical;
    if (m != null) {
      _bloodType = m['bloodType'] as String?;
      _alergias = m['hasRelevantAllergies'] as bool? ?? false;
      _alergiasDetail.text = m['allergiesDetail'] as String? ?? '';
      _condicion = m['hasRelevantMedicalCondition'] as bool? ?? false;
      _condicionDetail.text = m['medicalConditionDetail'] as String? ?? '';
      _medicacion = m['usesEmergencyMedication'] as bool? ?? false;
      _medicacionDetail.text = m['emergencyMedicationDetail'] as String? ?? '';
    }
  }

  @override
  void dispose() {
    _alergiasDetail.dispose();
    _condicionDetail.dispose();
    _medicacionDetail.dispose();
    super.dispose();
  }

  void _submit() {
    final medical = <String, dynamic>{
      if (_bloodType != null) 'bloodType': _bloodType,
      'hasRelevantAllergies': _alergias,
      if (_alergias && _alergiasDetail.text.isNotEmpty)
        'allergiesDetail': _alergiasDetail.text,
      'hasRelevantMedicalCondition': _condicion,
      if (_condicion && _condicionDetail.text.isNotEmpty)
        'medicalConditionDetail': _condicionDetail.text,
      'usesEmergencyMedication': _medicacion,
      if (_medicacion && _medicacionDetail.text.isNotEmpty)
        'emergencyMedicationDetail': _medicacionDetail.text,
    };
    widget.onNext(medical);
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('Información médica', style: AppTextStyles.headlineMedium),
          const SizedBox(height: 8),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: AppColors.primary.withValues(alpha: 0.08),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: AppColors.primary.withValues(alpha: 0.3)),
            ),
            child: Row(
              children: [
                const Icon(Icons.lock_outline,
                    size: 16, color: AppColors.primary),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Esta información es confidencial y solo se comparte en situaciones de emergencia.',
                    style: AppTextStyles.bodySmall
                        .copyWith(color: AppColors.primary),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          _bloodTypeSelector(),
          const SizedBox(height: 16),
          _boolField('¿Tiene alergias relevantes?', _alergias,
              (v) => setState(() => _alergias = v)),
          if (_alergias) ...[
            const SizedBox(height: 8),
            TextField(
              controller: _alergiasDetail,
              maxLines: 2,
              decoration: const InputDecoration(labelText: '¿Cuáles?'),
            ),
          ],
          const SizedBox(height: 12),
          _boolField('¿Tiene condición médica relevante?', _condicion,
              (v) => setState(() => _condicion = v)),
          if (_condicion) ...[
            const SizedBox(height: 8),
            TextField(
              controller: _condicionDetail,
              maxLines: 2,
              decoration: const InputDecoration(labelText: '¿Cuál?'),
            ),
          ],
          const SizedBox(height: 12),
          _boolField('¿Usa medicación de emergencia?', _medicacion,
              (v) => setState(() => _medicacion = v)),
          if (_medicacion) ...[
            const SizedBox(height: 8),
            TextField(
              controller: _medicacionDetail,
              maxLines: 2,
              decoration: const InputDecoration(labelText: '¿Cuál?'),
            ),
          ],
          const SizedBox(height: 32),
          AppButton(label: 'Continuar', onPressed: _submit),
          const SizedBox(height: 12),
          TextButton(
            onPressed: () => widget.onNext(null),
            child: Text('Omitir por ahora',
                style:
                    TextStyle(color: AppColors.mutedFg, fontSize: 14)),
          ),
        ],
      ),
    );
  }

  Widget _bloodTypeSelector() => GestureDetector(
        onTap: () async {
          final sel = await showModalBottomSheet<String>(
            context: context,
            backgroundColor: AppColors.background,
            shape: const RoundedRectangleBorder(
              borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
            ),
            builder: (_) => ListView(
              shrinkWrap: true,
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
              children: [
                Center(
                  child: Container(
                    width: 36, height: 4,
                    decoration: BoxDecoration(
                      color: AppColors.border,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                const Text('Tipo de sangre'),
                const SizedBox(height: 8),
                ..._bloodTypes.map((o) => ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(o),
                      trailing: o == _bloodType
                          ? const Icon(Icons.check, color: AppColors.primary)
                          : null,
                      onTap: () => Navigator.pop(context, o),
                    )),
              ],
            ),
          );
          if (sel != null) setState(() => _bloodType = sel);
        },
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
          decoration: BoxDecoration(
            color: AppColors.secondary,
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: AppColors.border),
          ),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Tipo de sangre (opcional)',
                        style: AppTextStyles.labelSmall
                            .copyWith(color: AppColors.mutedFg)),
                    const SizedBox(height: 2),
                    Text(
                      _bloodType ?? 'Seleccionar',
                      style: AppTextStyles.bodyMedium.copyWith(
                        color: _bloodType != null
                            ? AppColors.foreground
                            : AppColors.mutedFg,
                      ),
                    ),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right,
                  size: 18, color: AppColors.mutedFg),
            ],
          ),
        ),
      );

  Widget _boolField(
    String label,
    bool value,
    void Function(bool) onChanged,
  ) =>
      Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(
          color: AppColors.secondary,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: AppColors.border),
        ),
        child: Row(
          children: [
            Expanded(child: Text(label, style: AppTextStyles.bodyMedium)),
            Switch(
              value: value,
              activeThumbColor: AppColors.primary,
              onChanged: onChanged,
            ),
          ],
        ),
      );
}

// ─── Step 5: Final acceptances ────────────────────────────────────────────────

class _Step5FinalAcceptances extends ConsumerStatefulWidget {
  const _Step5FinalAcceptances({
    required this.accepted,
    required this.submitting,
    required this.onSubmit,
  });
  final List<String> accepted;
  final bool submitting;
  final void Function(List<String>) onSubmit;

  @override
  ConsumerState<_Step5FinalAcceptances> createState() =>
      _Step5FinalAcceptancesState();
}

class _Step5FinalAcceptancesState
    extends ConsumerState<_Step5FinalAcceptances> {
  List<LegalDoc>? _docs;
  bool _loading = true;
  final Set<String> _checked = {};

  @override
  void initState() {
    super.initState();
    _checked.addAll(widget.accepted);
    _loadDocs();
  }

  Future<void> _loadDocs() async {
    try {
      final ds = DocsLegalesRemoteDataSource(ref.read(authDioProvider));
      final docs = await Future.wait([
        ds.getActiveDocByCode('DATA_RETENTION_POLICY'),
        ds.getActiveDocByCode('LIABILITY_WAIVER'),
      ]);
      if (mounted) setState(() { _docs = docs; _loading = false; });
    } catch (_) {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final allChecked = _docs == null ||
        _docs!.isEmpty ||
        _docs!.every((d) => _checked.contains(d.id));

    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('Aceptaciones finales', style: AppTextStyles.headlineMedium),
          const SizedBox(height: 8),
          Text(
            'Lee y acepta los últimos documentos para completar tu registro.',
            style: AppTextStyles.bodyMedium.copyWith(color: AppColors.mutedFg),
          ),
          const SizedBox(height: 24),
          if (_loading)
            const Center(child: CircularProgressIndicator())
          else if (_docs == null || _docs!.isEmpty)
            Text('No hay documentos adicionales.',
                style: AppTextStyles.bodyMedium
                    .copyWith(color: AppColors.mutedFg))
          else
            ..._docs!.map((doc) => _DocCheckTile(
                  doc: doc,
                  checked: _checked.contains(doc.id),
                  onToggle: (v) => setState(() {
                    if (v) {
                      _checked.add(doc.id);
                    } else {
                      _checked.remove(doc.id);
                    }
                  }),
                )),
          const SizedBox(height: 32),
          AppButton(
            label: widget.submitting ? 'Registrando...' : 'Completar registro',
            loading: widget.submitting,
            onPressed:
                allChecked && !widget.submitting
                    ? () => widget.onSubmit(_checked.toList())
                    : null,
          ),
        ],
      ),
    );
  }
}

// ─── Success screen ───────────────────────────────────────────────────────────

class _SuccessScreen extends StatelessWidget {
  const _SuccessScreen();

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: AppColors.background,
        body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Icon(Icons.check_circle_outline,
                    size: 80, color: AppColors.primary),
                const SizedBox(height: 24),
                Text(
                  '¡Registro completado!',
                  style: AppTextStyles.headlineLarge,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 12),
                Text(
                  'Tu cuenta ha sido activada. Ya puedes iniciar sesión.',
                  style: AppTextStyles.bodyMedium
                      .copyWith(color: AppColors.mutedFg),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 40),
                AppButton(
                  label: 'Ir al inicio de sesión',
                  onPressed: () => context.go('/login'),
                ),
              ],
            ),
          ),
        ),
      );
}
