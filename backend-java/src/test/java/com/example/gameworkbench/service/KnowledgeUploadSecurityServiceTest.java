package com.example.gameworkbench.service;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.example.gameworkbench.common.exception.BusinessException;
class KnowledgeUploadSecurityServiceTest {
 private final KnowledgeUploadSecurityService service = new KnowledgeUploadSecurityService();
 @Test void acceptsOnlySniffedAllowedTypesAndHashes() { assertThat(service.validate("rules.md", "text/markdown", "hello".getBytes()).contentHash()).hasSize(64); assertThat(service.validate("rules.pdf", "application/pdf", "%PDF-1.7".getBytes()).extension()).isEqualTo("pdf"); }
 @Test void rejectsTraversalForgedMimeAndBinaryText() { assertThatThrownBy(() -> service.validate("../x.md", "text/markdown", "x".getBytes())).isInstanceOf(BusinessException.class); assertThatThrownBy(() -> service.validate("x.pdf", "application/pdf", "not pdf".getBytes())).isInstanceOf(BusinessException.class); assertThatThrownBy(() -> service.validate("x.txt", "text/plain", new byte[] {0})).isInstanceOf(BusinessException.class); }
}
