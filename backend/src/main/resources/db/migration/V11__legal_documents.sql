-- =============================================================================
-- V11__legal_documents.sql
-- Módulo de gestión documental: tablas de documentos legales versionados
-- y aceptaciones electrónicas. Incluye seed de los 4 documentos iniciales.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- legal_documents: documentos legales versionados (contenido en Markdown)
-- ---------------------------------------------------------------------------

CREATE TABLE public.legal_documents (
    id                                    uuid DEFAULT gen_random_uuid() NOT NULL,
    code                                  character varying(50) NOT NULL,
    title                                 character varying(255) NOT NULL,
    description                           text,
    document_type                         character varying(50) NOT NULL,
    required_stage                        character varying(30) NOT NULL,
    version                               integer NOT NULL DEFAULT 1,
    content                               text NOT NULL,
    content_hash                          character varying(64) NOT NULL,
    active                                boolean NOT NULL DEFAULT false,
    required                              boolean NOT NULL DEFAULT true,
    requires_reacceptance_on_new_version  boolean NOT NULL DEFAULT true,
    approved_at                           timestamp without time zone,
    approved_by                           uuid,
    created_at                            timestamp without time zone DEFAULT now() NOT NULL,
    updated_at                            timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT legal_documents_pkey PRIMARY KEY (id),
    CONSTRAINT legal_documents_code_version_key UNIQUE (code, version),
    CONSTRAINT chk_required_stage CHECK (required_stage IN ('REGISTRATION', 'PROFILE_COMPLETION', 'ACTIVITY_ENROLLMENT')),
    CONSTRAINT fk_legal_documents_approved_by FOREIGN KEY (approved_by) REFERENCES public.socios(id)
);

CREATE INDEX idx_legal_documents_code_active ON public.legal_documents (code, active);

-- ---------------------------------------------------------------------------
-- legal_document_acceptances: registro inmutable de aceptaciones electrónicas
-- IP, user-agent y hash son capturados en backend — nunca en frontend.
-- ---------------------------------------------------------------------------

CREATE TABLE public.legal_document_acceptances (
    id                  uuid DEFAULT gen_random_uuid() NOT NULL,
    socio_id            uuid NOT NULL,
    legal_document_id   uuid NOT NULL,
    document_code       character varying(50) NOT NULL,
    document_version    integer NOT NULL,
    content_hash        character varying(64) NOT NULL,
    accepted_at         timestamp without time zone DEFAULT now() NOT NULL,
    ip_address          character varying(45),
    user_agent          text,
    accepted            boolean NOT NULL DEFAULT true,
    created_at          timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT legal_document_acceptances_pkey PRIMARY KEY (id),
    CONSTRAINT fk_lda_socio FOREIGN KEY (socio_id) REFERENCES public.socios(id),
    CONSTRAINT fk_lda_document FOREIGN KEY (legal_document_id) REFERENCES public.legal_documents(id)
);

CREATE INDEX idx_lda_socio_id ON public.legal_document_acceptances (socio_id);
CREATE INDEX idx_lda_document_code ON public.legal_document_acceptances (document_code, document_version);

-- ---------------------------------------------------------------------------
-- Seed: documentos legales iniciales (versión 1, activos)
-- El hash se calcula en PostgreSQL sobre el contenido almacenado.
-- ---------------------------------------------------------------------------

INSERT INTO public.legal_documents (
    id, code, title, description, document_type, required_stage,
    version, content, content_hash,
    active, required, requires_reacceptance_on_new_version,
    approved_at, approved_by, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    'DATA_PROCESSING_POLICY',
    'Política de Tratamiento de Datos Personales',
    'Autorización para el tratamiento de datos personales de aspirantes y socios del Club.',
    'DATA_PROCESSING_POLICY',
    'REGISTRATION',
    1,
    $policy$# AUTORIZACIÓN PARA EL TRATAMIENTO DE DATOS PERSONALES

Al registrarme como aspirante o socio del Club Deportivo Especializado Formativo El Sadday (en adelante, "el Club"), declaro que he sido informado de manera clara y comprensible sobre el tratamiento de mis datos personales.

Entiendo que el Club recopilará y utilizará información necesaria para la gestión de mi membresía, la organización de actividades deportivas, la comunicación institucional, la atención de emergencias y el cumplimiento de obligaciones administrativas, estatutarias y legales aplicables.

La información recopilada podrá incluir, entre otros:

* Datos de identificación personal.
* Datos de contacto.
* Información relacionada con mi membresía y participación en actividades.
* Contactos de emergencia.
* Información médica o de salud que voluntariamente proporcione para fines de seguridad durante actividades organizadas por el Club.

El Club utilizará mis datos personales únicamente para las siguientes finalidades:

* Gestionar mi proceso de admisión, permanencia y participación en las actividades de la organización.
* Mantener comunicación relacionada con actividades, eventos, capacitaciones, reuniones y asuntos administrativos.
* Gestionar situaciones de emergencia que puedan presentarse durante actividades organizadas por el Club.
* Cumplir obligaciones legales, reglamentarias o estatutarias aplicables a la organización.
* Mantener registros históricos, administrativos y financieros relacionados con la membresía.

El Club adoptará medidas razonables de seguridad para proteger la información personal contra pérdida, acceso no autorizado, alteración, divulgación o uso indebido.

La conservación, actualización y eliminación de mis datos personales se realizará conforme a la Política de Conservación y Eliminación de Datos Personales vigente del Club.

Como titular de los datos personales, conozco que podré solicitar el acceso, actualización, rectificación o eliminación de mis datos personales de conformidad con la normativa aplicable y las políticas internas del Club.

Declaro que la información proporcionada es veraz y que he leído, comprendido y aceptado el contenido de la presente autorización.$policy$,
    encode(sha256(convert_to($policy_hash$# AUTORIZACIÓN PARA EL TRATAMIENTO DE DATOS PERSONALES

Al registrarme como aspirante o socio del Club Deportivo Especializado Formativo El Sadday (en adelante, "el Club"), declaro que he sido informado de manera clara y comprensible sobre el tratamiento de mis datos personales.

Entiendo que el Club recopilará y utilizará información necesaria para la gestión de mi membresía, la organización de actividades deportivas, la comunicación institucional, la atención de emergencias y el cumplimiento de obligaciones administrativas, estatutarias y legales aplicables.

La información recopilada podrá incluir, entre otros:

* Datos de identificación personal.
* Datos de contacto.
* Información relacionada con mi membresía y participación en actividades.
* Contactos de emergencia.
* Información médica o de salud que voluntariamente proporcione para fines de seguridad durante actividades organizadas por el Club.

El Club utilizará mis datos personales únicamente para las siguientes finalidades:

* Gestionar mi proceso de admisión, permanencia y participación en las actividades de la organización.
* Mantener comunicación relacionada con actividades, eventos, capacitaciones, reuniones y asuntos administrativos.
* Gestionar situaciones de emergencia que puedan presentarse durante actividades organizadas por el Club.
* Cumplir obligaciones legales, reglamentarias o estatutarias aplicables a la organización.
* Mantener registros históricos, administrativos y financieros relacionados con la membresía.

El Club adoptará medidas razonables de seguridad para proteger la información personal contra pérdida, acceso no autorizado, alteración, divulgación o uso indebido.

La conservación, actualización y eliminación de mis datos personales se realizará conforme a la Política de Conservación y Eliminación de Datos Personales vigente del Club.

Como titular de los datos personales, conozco que podré solicitar el acceso, actualización, rectificación o eliminación de mis datos personales de conformidad con la normativa aplicable y las políticas internas del Club.

Declaro que la información proporcionada es veraz y que he leído, comprendido y aceptado el contenido de la presente autorización.$policy_hash$, 'UTF-8')), 'hex'),
    true, true, true,
    now(), NULL, now(), now();

INSERT INTO public.legal_documents (
    id, code, title, description, document_type, required_stage,
    version, content, content_hash,
    active, required, requires_reacceptance_on_new_version,
    approved_at, approved_by, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    'MEDICAL_DATA_CONSENT',
    'Consentimiento para el Tratamiento de Datos de Salud',
    'Autorización expresa para el tratamiento de información médica o de salud con fines de seguridad en actividades.',
    'MEDICAL_DATA_CONSENT',
    'REGISTRATION',
    1,
    $medical$# CONSENTIMIENTO PARA EL TRATAMIENTO DE DATOS DE SALUD

Al registrarme como aspirante o socio del Club Deportivo Especializado Formativo El Sadday, otorgo expresamente mi autorización para el tratamiento de la información médica o de salud que voluntariamente proporcione.

Entiendo que:

* Esta información será utilizada exclusivamente para fines de prevención, seguridad y atención de emergencias durante el desarrollo de actividades organizadas por el Club.
* El acceso a esta información estará restringido únicamente a las personas autorizadas por el Club que requieran conocerla para la planificación de actividades o la atención de emergencias.
* El Club adoptará medidas razonables de seguridad para proteger esta información contra pérdida, acceso no autorizado, alteración, divulgación o uso indebido.
* La conservación, actualización y eliminación de esta información se realizará conforme a la Política de Conservación y Eliminación de Datos Personales vigente del Club.
* Como titular de los datos, podré solicitar en cualquier momento el acceso, actualización, rectificación o eliminación de mi información de salud.

La provisión de esta información es voluntaria. No estoy obligado a proporcionarla, aunque puede ser necesaria para garantizar mi seguridad y la de otros participantes en actividades de montaña.

Declaro que he leído, comprendido y acepto expresamente el presente consentimiento para el tratamiento de mis datos de salud.$medical$,
    encode(sha256(convert_to($medical_hash$# CONSENTIMIENTO PARA EL TRATAMIENTO DE DATOS DE SALUD

Al registrarme como aspirante o socio del Club Deportivo Especializado Formativo El Sadday, otorgo expresamente mi autorización para el tratamiento de la información médica o de salud que voluntariamente proporcione.

Entiendo que:

* Esta información será utilizada exclusivamente para fines de prevención, seguridad y atención de emergencias durante el desarrollo de actividades organizadas por el Club.
* El acceso a esta información estará restringido únicamente a las personas autorizadas por el Club que requieran conocerla para la planificación de actividades o la atención de emergencias.
* El Club adoptará medidas razonables de seguridad para proteger esta información contra pérdida, acceso no autorizado, alteración, divulgación o uso indebido.
* La conservación, actualización y eliminación de esta información se realizará conforme a la Política de Conservación y Eliminación de Datos Personales vigente del Club.
* Como titular de los datos, podré solicitar en cualquier momento el acceso, actualización, rectificación o eliminación de mi información de salud.

La provisión de esta información es voluntaria. No estoy obligado a proporcionarla, aunque puede ser necesaria para garantizar mi seguridad y la de otros participantes en actividades de montaña.

Declaro que he leído, comprendido y acepto expresamente el presente consentimiento para el tratamiento de mis datos de salud.$medical_hash$, 'UTF-8')), 'hex'),
    true, true, true,
    now(), NULL, now(), now();

INSERT INTO public.legal_documents (
    id, code, title, description, document_type, required_stage,
    version, content, content_hash,
    active, required, requires_reacceptance_on_new_version,
    approved_at, approved_by, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    'DATA_RETENTION_POLICY',
    'Política de Conservación y Eliminación de Datos Personales',
    'Política que establece las normas de almacenamiento, conservación y eliminación de datos personales.',
    'DATA_RETENTION_POLICY',
    'PROFILE_COMPLETION',
    1,
    $retention$# POLÍTICA DE CONSERVACIÓN Y ELIMINACIÓN DE DATOS PERSONALES

## CLUB DEPORTIVO ESPECIALIZADO FORMATIVO EL SADDAY

### 1. OBJETIVO

La presente Política de Conservación y Eliminación de Datos Personales tiene por objeto establecer las normas aplicables al almacenamiento, uso, conservación, actualización y eliminación de la información personal proporcionada por aspirantes, socios activos, socios juveniles, socios vitalicios y exsocios del Club Deportivo Especializado Formativo El Sadday (en adelante, "el Club").

Esta política forma parte de las medidas adoptadas por el Club para proteger la privacidad de sus miembros y garantizar el tratamiento responsable de la información recopilada durante la relación con la organización.

### 2. DATOS RECOPILADOS

El Club podrá recopilar y almacenar información necesaria para la administración de la membresía y el desarrollo de sus actividades, incluyendo:

* Datos de identificación personal.
* Datos de contacto.
* Información relacionada con la membresía.
* Información de contacto para emergencias.
* Información médica o de salud proporcionada voluntariamente para fines de seguridad en actividades deportivas.
* Registros de participación en actividades organizadas por el Club.
* Información sobre cargos o dignidades desempeñadas dentro de la organización.
* Registros administrativos, financieros y contables relacionados con la membresía.

### 3. CONSERVACIÓN DE LA INFORMACIÓN

Mientras exista una relación activa entre el socio y el Club, la información podrá ser conservada y utilizada para fines administrativos, deportivos, organizativos, de comunicación, seguridad, gestión de emergencias y cumplimiento de las obligaciones establecidas en los estatutos, reglamentos internos y normativa aplicable.

El acceso a los datos estará limitado a las personas autorizadas por el Club y únicamente para el cumplimiento de sus funciones.

### 4. BAJA VOLUNTARIA DEL SOCIO

Todo socio podrá retirarse voluntariamente del Club en cualquier momento.

Para ello deberá remitir una solicitud formal al correo electrónico oficial de la Secretaría del Club, indicando expresamente su voluntad de finalizar su membresía.

La solicitud deberá contener al menos:

* Nombres y apellidos completos.
* Número de cédula o documento de identidad.
* Correo electrónico registrado en el Club.
* Solicitud expresa de baja voluntaria y, de ser el caso, de eliminación de sus datos personales.

La Secretaría podrá solicitar información adicional razonable para verificar la identidad del solicitante.

### 5. CONSERVACIÓN DE DATOS DESPUÉS DE LA BAJA

La baja voluntaria del socio no implica la eliminación inmediata de toda la información registrada por el Club.

El Club podrá conservar únicamente la información estrictamente necesaria cuando exista una finalidad legítima para hacerlo, incluyendo obligaciones económicas pendientes, procesos administrativos o disciplinarios en curso, requerimientos legales o regulatorios, necesidades contables o de auditoría, y conservación de registros históricos institucionales.

La existencia de obligaciones económicas pendientes no impedirá la baja voluntaria del socio.

### 6. ELIMINACIÓN DE DATOS PERSONALES

Una vez recibida y validada la solicitud, el Club eliminará o anonimizará la información que ya no resulte necesaria, incluyendo: información médica o de salud, contactos de emergencia, fotografías utilizadas exclusivamente para fines administrativos, y datos personales que no resulten necesarios para fines históricos, legales o administrativos.

### 7. INFORMACIÓN QUE PODRÁ CONSERVARSE

Aun cuando el socio haya solicitado la eliminación de sus datos, el Club podrá conservar: nombres y apellidos, número de identificación, fechas de ingreso y salida, categorías de membresía, cargos o dignidades desempeñadas, registros históricos institucionales, y registros financieros, contables o tributarios.

### 8. INFORMACIÓN MÉDICA

La información médica será utilizada exclusivamente para fines de seguridad, prevención y atención de emergencias. Una vez finalizada la relación, dicha información será eliminada cuando ya no exista una finalidad legítima para su conservación.

### 9. ACEPTACIÓN

Al registrarse como aspirante o socio del Club, la persona declara haber leído, comprendido y aceptado la presente Política de Conservación y Eliminación de Datos Personales.$retention$,
    encode(sha256(convert_to($retention_hash$# POLÍTICA DE CONSERVACIÓN Y ELIMINACIÓN DE DATOS PERSONALES

## CLUB DEPORTIVO ESPECIALIZADO FORMATIVO EL SADDAY

### 1. OBJETIVO

La presente Política de Conservación y Eliminación de Datos Personales tiene por objeto establecer las normas aplicables al almacenamiento, uso, conservación, actualización y eliminación de la información personal proporcionada por aspirantes, socios activos, socios juveniles, socios vitalicios y exsocios del Club Deportivo Especializado Formativo El Sadday (en adelante, "el Club").

Esta política forma parte de las medidas adoptadas por el Club para proteger la privacidad de sus miembros y garantizar el tratamiento responsable de la información recopilada durante la relación con la organización.

### 2. DATOS RECOPILADOS

El Club podrá recopilar y almacenar información necesaria para la administración de la membresía y el desarrollo de sus actividades, incluyendo:

* Datos de identificación personal.
* Datos de contacto.
* Información relacionada con la membresía.
* Información de contacto para emergencias.
* Información médica o de salud proporcionada voluntariamente para fines de seguridad en actividades deportivas.
* Registros de participación en actividades organizadas por el Club.
* Información sobre cargos o dignidades desempeñadas dentro de la organización.
* Registros administrativos, financieros y contables relacionados con la membresía.

### 3. CONSERVACIÓN DE LA INFORMACIÓN

Mientras exista una relación activa entre el socio y el Club, la información podrá ser conservada y utilizada para fines administrativos, deportivos, organizativos, de comunicación, seguridad, gestión de emergencias y cumplimiento de las obligaciones establecidas en los estatutos, reglamentos internos y normativa aplicable.

El acceso a los datos estará limitado a las personas autorizadas por el Club y únicamente para el cumplimiento de sus funciones.

### 4. BAJA VOLUNTARIA DEL SOCIO

Todo socio podrá retirarse voluntariamente del Club en cualquier momento.

Para ello deberá remitir una solicitud formal al correo electrónico oficial de la Secretaría del Club, indicando expresamente su voluntad de finalizar su membresía.

La solicitud deberá contener al menos:

* Nombres y apellidos completos.
* Número de cédula o documento de identidad.
* Correo electrónico registrado en el Club.
* Solicitud expresa de baja voluntaria y, de ser el caso, de eliminación de sus datos personales.

La Secretaría podrá solicitar información adicional razonable para verificar la identidad del solicitante.

### 5. CONSERVACIÓN DE DATOS DESPUÉS DE LA BAJA

La baja voluntaria del socio no implica la eliminación inmediata de toda la información registrada por el Club.

El Club podrá conservar únicamente la información estrictamente necesaria cuando exista una finalidad legítima para hacerlo, incluyendo obligaciones económicas pendientes, procesos administrativos o disciplinarios en curso, requerimientos legales o regulatorios, necesidades contables o de auditoría, y conservación de registros históricos institucionales.

La existencia de obligaciones económicas pendientes no impedirá la baja voluntaria del socio.

### 6. ELIMINACIÓN DE DATOS PERSONALES

Una vez recibida y validada la solicitud, el Club eliminará o anonimizará la información que ya no resulte necesaria, incluyendo: información médica o de salud, contactos de emergencia, fotografías utilizadas exclusivamente para fines administrativos, y datos personales que no resulten necesarios para fines históricos, legales o administrativos.

### 7. INFORMACIÓN QUE PODRÁ CONSERVARSE

Aun cuando el socio haya solicitado la eliminación de sus datos, el Club podrá conservar: nombres y apellidos, número de identificación, fechas de ingreso y salida, categorías de membresía, cargos o dignidades desempeñadas, registros históricos institucionales, y registros financieros, contables o tributarios.

### 8. INFORMACIÓN MÉDICA

La información médica será utilizada exclusivamente para fines de seguridad, prevención y atención de emergencias. Una vez finalizada la relación, dicha información será eliminada cuando ya no exista una finalidad legítima para su conservación.

### 9. ACEPTACIÓN

Al registrarse como aspirante o socio del Club, la persona declara haber leído, comprendido y aceptado la presente Política de Conservación y Eliminación de Datos Personales.$retention_hash$, 'UTF-8')), 'hex'),
    true, true, true,
    now(), NULL, now(), now();

INSERT INTO public.legal_documents (
    id, code, title, description, document_type, required_stage,
    version, content, content_hash,
    active, required, requires_reacceptance_on_new_version,
    approved_at, approved_by, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    'LIABILITY_WAIVER',
    'Declaración de Conocimiento de Riesgos y Descargo de Responsabilidad',
    'Declaración en la que el socio reconoce los riesgos inherentes a las actividades de montaña y libera al Club de responsabilidad por riesgos previsibles.',
    'LIABILITY_WAIVER',
    'PROFILE_COMPLETION',
    1,
    $waiver$# DECLARACIÓN DE CONOCIMIENTO DE RIESGOS Y DESCARGO DE RESPONSABILIDAD

Antes de continuar con su registro como socio o aspirante del Club Deportivo Especializado Formativo El Sadday, por favor lea atentamente la siguiente información:

Las actividades organizadas por el Club, incluyendo montañismo, senderismo, excursionismo, campismo, escalada, ciclismo de montaña y otras actividades al aire libre, implican riesgos inherentes que no pueden eliminarse completamente.

Estos riesgos pueden incluir, entre otros:

- Caídas, resbalones o golpes.
- Lesiones musculares o articulares.
- Hipotermia, insolación o agotamiento físico.
- Reacciones alérgicas.
- Accidentes durante desplazamientos.
- Condiciones climáticas adversas.
- Emergencias médicas.
- Lesiones graves, incapacidad permanente o fallecimiento.

Al aceptar esta declaración, usted reconoce y acepta que:

1. Participa en las actividades del Club de manera libre y voluntaria.

2. Es responsable de evaluar su estado físico, mental y médico antes de participar en cualquier actividad.

3. Es responsable de informar al Club sobre cualquier condición médica relevante que pudiera afectar su seguridad o la de terceros.

4. El Club no presta servicios médicos, de rescate profesional ni cobertura de seguros médicos para los participantes.

5. Los gastos derivados de atención médica, rescate, evacuación, hospitalización, medicamentos, transporte o cualquier otra asistencia requerida como consecuencia de un accidente o emergencia serán de exclusiva responsabilidad del participante.

6. El Club realiza esfuerzos razonables para promover actividades seguras, pero no garantiza la ausencia de riesgos ni de accidentes.

7. Usted asume los riesgos inherentes asociados a la práctica de actividades de montaña y actividades al aire libre.

8. Libera al Club, sus directivos, coordinadores, instructores, voluntarios y colaboradores de reclamaciones derivadas de los riesgos inherentes y previsibles propios de las actividades desarrolladas.

9. Esta declaración no limita ni excluye las responsabilidades que no puedan ser excluidas conforme a la legislación ecuatoriana aplicable.

Al seleccionar la opción de aceptación, usted declara que ha leído, comprendido y aceptado íntegramente el contenido de esta declaración.$waiver$,
    encode(sha256(convert_to($waiver_hash$# DECLARACIÓN DE CONOCIMIENTO DE RIESGOS Y DESCARGO DE RESPONSABILIDAD

Antes de continuar con su registro como socio o aspirante del Club Deportivo Especializado Formativo El Sadday, por favor lea atentamente la siguiente información:

Las actividades organizadas por el Club, incluyendo montañismo, senderismo, excursionismo, campismo, escalada, ciclismo de montaña y otras actividades al aire libre, implican riesgos inherentes que no pueden eliminarse completamente.

Estos riesgos pueden incluir, entre otros:

- Caídas, resbalones o golpes.
- Lesiones musculares o articulares.
- Hipotermia, insolación o agotamiento físico.
- Reacciones alérgicas.
- Accidentes durante desplazamientos.
- Condiciones climáticas adversas.
- Emergencias médicas.
- Lesiones graves, incapacidad permanente o fallecimiento.

Al aceptar esta declaración, usted reconoce y acepta que:

1. Participa en las actividades del Club de manera libre y voluntaria.

2. Es responsable de evaluar su estado físico, mental y médico antes de participar en cualquier actividad.

3. Es responsable de informar al Club sobre cualquier condición médica relevante que pudiera afectar su seguridad o la de terceros.

4. El Club no presta servicios médicos, de rescate profesional ni cobertura de seguros médicos para los participantes.

5. Los gastos derivados de atención médica, rescate, evacuación, hospitalización, medicamentos, transporte o cualquier otra asistencia requerida como consecuencia de un accidente o emergencia serán de exclusiva responsabilidad del participante.

6. El Club realiza esfuerzos razonables para promover actividades seguras, pero no garantiza la ausencia de riesgos ni de accidentes.

7. Usted asume los riesgos inherentes asociados a la práctica de actividades de montaña y actividades al aire libre.

8. Libera al Club, sus directivos, coordinadores, instructores, voluntarios y colaboradores de reclamaciones derivadas de los riesgos inherentes y previsibles propios de las actividades desarrolladas.

9. Esta declaración no limita ni excluye las responsabilidades que no puedan ser excluidas conforme a la legislación ecuatoriana aplicable.

Al seleccionar la opción de aceptación, usted declara que ha leído, comprendido y aceptado íntegramente el contenido de esta declaración.$waiver_hash$, 'UTF-8')), 'hex'),
    true, true, true,
    now(), NULL, now(), now();
