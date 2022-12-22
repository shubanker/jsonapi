package io.stargate.sgv3.docsapi.service.shredding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.stargate.sgv2.common.testprofiles.NoGlobalResourcesTestProfile;
import io.stargate.sgv3.docsapi.exception.ErrorCode;
import io.stargate.sgv3.docsapi.service.shredding.model.WritableShreddedDocument;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(NoGlobalResourcesTestProfile.Impl.class)
public class ShredderTest {
  @Inject ObjectMapper objectMapper;

  @Inject Shredder shredder;

  @Nested
  class OkCases {
    @Test
    public void simpleShredFromPathExample() throws Exception {
      final String inputJson =
          """
                      { "_id" : "abc",
                        "name" : "Bob",
                        "values" : [ 1, 2 ],
                        "[extra.stuff]" : true,
                        "nullable" : null
                      }
                      """;
      WritableShreddedDocument doc = shredder.shred(objectMapper.readTree(inputJson));
      assertThat(doc.id()).isEqualTo("abc");
      List<JsonPath> expPaths =
          Arrays.asList(
              JsonPath.from("name"),
              JsonPath.from("values"),
              JsonPath.from("values.[0]"),
              JsonPath.from("values.[1]"),
              JsonPath.from("\\[extra\\.stuff]"),
              JsonPath.from("nullable"));

      // First verify paths
      assertThat(doc.docFieldOrder()).isEqualTo(expPaths);
      assertThat(doc.existKeys()).isEqualTo(new HashSet<>(expPaths));

      // Atomic value counts (5 atomic fields, _id not included)
      assertThat(doc.docAtomicFields()).hasSize(5);

      // Then array info (doc has one array, with 2 elements)
      assertThat(doc.arraySize())
          .hasSize(1)
          .containsEntry(JsonPath.from("values"), Integer.valueOf(2));
      assertThat(doc.arrayEquals()).hasSize(1);
      assertThat(doc.arrayContains()).hasSize(2);

      // Sub-documents (Object values): none in this example
      assertThat(doc.subDocEquals()).hasSize(0);

      // Then atomic value containers
      assertThat(doc.queryBoolValues())
          .isEqualTo(Collections.singletonMap(JsonPath.from("\\[extra\\.stuff]"), Boolean.TRUE));
      Map<JsonPath, BigDecimal> expNums = new LinkedHashMap<>();
      expNums.put(JsonPath.from("values.[0]"), BigDecimal.valueOf(1));
      expNums.put(JsonPath.from("values.[1]"), BigDecimal.valueOf(2));
      assertThat(doc.queryNumberValues()).isEqualTo(expNums);
      assertThat(doc.queryTextValues())
          .isEqualTo(Collections.singletonMap(JsonPath.from("name"), "Bob"));
      assertThat(doc.queryNullValues()).isEqualTo(Collections.singleton(JsonPath.from("nullable")));
    }
  }

  @Nested
  class ErrorCases {

    @Test
    public void docBadJSONType() {
      Throwable t = catchThrowable(() -> shredder.shred(objectMapper.readTree("[ 1, 2 ]")));

      assertThat(t)
          .isNotNull()
          .hasMessage(
              "Bad document type to shred: Document to shred must be a JSON Object, instead got ARRAY")
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SHRED_BAD_DOCUMENT_TYPE);
    }

    @Test
    public void docBadDocIdType() {
      Throwable t =
          catchThrowable(() -> shredder.shred(objectMapper.readTree("{ \"_id\" : [ ] }")));

      assertThat(t)
          .isNotNull()
          .hasMessage(
              "Bad type for '_id' property: Document Id must be a JSON String, instead got ARRAY")
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SHRED_BAD_DOCID_TYPE);
    }
  }
}
