package com.sadday.app.shared.pdf;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xhtmlrenderer.pdf.ITextRenderer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Genera bytes PDF a partir de templates HTML Thymeleaf.
 *
 * <p>Usa Thymeleaf en modo standalone cargando templates desde
 * {@code classpath:templates/pdf/*.html}. Flying Saucer ({@link ITextRenderer})
 * convierte el XHTML resultante en PDF usando OpenPDF.
 *
 * <p>El HTML se parsea con un {@link DocumentBuilder} hardened contra XXE antes
 * de pasárselo a Flying Saucer, evitando que el parser interno de la librería
 * procese entidades externas o DTDs remotas.
 */
@Slf4j
@Service
public class PdfRenderService {

    private final TemplateEngine templateEngine;

    /** Logo bundled como recurso del JAR, cacheado en base64 para inyectarlo en cada PDF. */
    private final String headerImageBase64;

    // DocumentBuilderFactory es thread-safe para newDocumentBuilder(); se crea una vez.
    private static final DocumentBuilderFactory XML_FACTORY = buildHardenedFactory();

    private static final String HEADER_IMAGE_PATH = "static/images/header-pdf.png";

    public PdfRenderService() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/pdf/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(true);

        this.templateEngine = new TemplateEngine();
        this.templateEngine.setTemplateResolver(resolver);
        this.headerImageBase64 = loadHeaderImage();
    }

    private static String loadHeaderImage() {
        try (InputStream in = new ClassPathResource(HEADER_IMAGE_PATH).getInputStream()) {
            return Base64.getEncoder().encodeToString(in.readAllBytes());
        } catch (Exception e) {
            log.warn("No se pudo cargar el header del PDF ({}): los documentos saldrán sin logo", HEADER_IMAGE_PATH, e);
            return null;
        }
    }

    /**
     * Renderiza el template Thymeleaf y lo convierte a bytes PDF.
     *
     * @param templateName nombre del template sin extensión (ej: {@code "informe-salida"})
     * @param variables    variables inyectadas en el template
     * @return bytes del PDF generado
     */
    public byte[] render(String templateName, Map<String, Object> variables) {
        // Inyectar el logo por defecto en todos los renders. Si el caller ya lo
        // setea (incluso a null para suprimirlo), respetamos esa decisión.
        Map<String, Object> merged = new HashMap<>(variables);
        merged.putIfAbsent("headerImageBase64", headerImageBase64);

        Context ctx = new Context(Locale.of("es", "EC"), merged);
        String html = templateEngine.process(templateName, ctx);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Parsear el HTML con un DocumentBuilder hardened (XXE deshabilitado) y
            // pasar el DOM ya construido a ITextRenderer en lugar de setDocumentFromString(),
            // que usaría el parser interno de Flying Saucer sin restricciones.
            DocumentBuilder builder = XML_FACTORY.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            Document doc = builder.parse(new InputSource(new StringReader(html)));

            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocument(doc, null);
            renderer.layout();
            renderer.createPDF(out);
            byte[] bytes = out.toByteArray();
            log.debug("PDF generado desde template '{}': {} bytes", templateName, bytes.length);
            return bytes;
        } catch (Exception e) {
            throw new IllegalStateException("Error generando PDF desde template: " + templateName, e);
        }
    }

    private static DocumentBuilderFactory buildHardenedFactory() {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        try {
            // OWASP XXE Prevention — deshabilitar entidades externas y DTDs remotas.
            // No usamos disallow-doctype-decl porque los templates tienen <!DOCTYPE html>.
            f.setFeature("http://xml.org/sax/features/external-general-entities",        false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities",      false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (ParserConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        return f;
    }
}
