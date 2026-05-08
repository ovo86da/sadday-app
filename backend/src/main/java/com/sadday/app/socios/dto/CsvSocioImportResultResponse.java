package com.sadday.app.socios.dto;

import java.util.List;

public record CsvSocioImportResultResponse(
        int importados,
        int omitidos,
        List<CsvSocioImportPreviewResponse.FilaError> errores
) {}
