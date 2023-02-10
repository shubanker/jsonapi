package io.stargate.sgv2.jsonapi.api.model.command.deserializers;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.stargate.sgv2.jsonapi.api.model.command.clause.filter.ComparisonExpression;
import io.stargate.sgv2.jsonapi.api.model.command.clause.filter.FilterClause;
import io.stargate.sgv2.jsonapi.api.model.command.clause.filter.FilterOperator;
import io.stargate.sgv2.jsonapi.config.constants.DocumentConstants;
import io.stargate.sgv2.jsonapi.exception.ErrorCode;
import io.stargate.sgv2.jsonapi.exception.JsonApiException;
import io.stargate.sgv2.jsonapi.service.shredding.model.DocumentId;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link StdDeserializer} for the {@link FilterClause}. */
public class FilterClauseDeserializer extends StdDeserializer<FilterClause> {

  public FilterClauseDeserializer() {
    super(FilterClause.class);
  }

  /**
   * {@inheritDoc} Filter clause can follow short cut {"field" : "value"} instead of {"field" :
   * {"$eq" : "value"}}
   */
  @Override
  public FilterClause deserialize(
      JsonParser jsonParser, DeserializationContext deserializationContext)
      throws IOException, JacksonException {
    JsonNode filterNode = deserializationContext.readTree(jsonParser);
    if (!filterNode.isObject()) throw new JsonApiException(ErrorCode.UNSUPPORTED_FILTER_DATA_TYPE);
    Iterator<Map.Entry<String, JsonNode>> fieldIter = filterNode.fields();
    List<ComparisonExpression> expressionList = new ArrayList<>();
    while (fieldIter.hasNext()) {
      Map.Entry<String, JsonNode> entry = fieldIter.next();
      // TODO: Does not handle logical expressions, they are out of scope
      JsonNode operatorExpression = entry.getValue();
      if (operatorExpression.isObject()) {
        expressionList.add(createComparisonExpression(entry));
      } else {
        // @TODO: Need to add array value type to this condition
        expressionList.add(
            ComparisonExpression.eq(
                entry.getKey(), jsonNodeValue(entry.getKey(), entry.getValue())));
      }
    }
    return new FilterClause(expressionList);
  }

  /**
   * The filter clause is entry will have field path as key and object type as value. The value can
   * have multiple operator and condition values.
   *
   * <p>Eg 1: {"field" : {"$eq" : "value"}}
   *
   * <p>Eg 2: {"field" : {"$gt" : 10, "$lt" : 50}}
   *
   * @param entry
   * @return
   */
  private ComparisonExpression createComparisonExpression(Map.Entry<String, JsonNode> entry) {
    ComparisonExpression expression = new ComparisonExpression(entry.getKey(), new ArrayList<>());
    final Iterator<Map.Entry<String, JsonNode>> fields = entry.getValue().fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> updateField = fields.next();
      FilterOperator operator = null;
      try {
        operator = FilterOperator.FilterOperatorUtils.getComparisonOperator(updateField.getKey());
      } catch (JsonApiException exception) {
        // If getComparisonOperator returns an exception, check for subdocument equality condition,
        // this will happen when shortcut is used "filter" : { "size" : { "w": 21, "h": 14} }
        if (updateField.getKey().startsWith("$")) {
          throw exception;
        } else {
          return ComparisonExpression.eq(entry.getKey(), jsonNodeValue(entry.getValue()));
        }
      }
      JsonNode value = updateField.getValue();
      // @TODO: Need to add array and sub-document value type to this condition
      expression.add(operator, jsonNodeValue(entry.getKey(), value));
    }
    return expression;
  }

  private static Object jsonNodeValue(String path, JsonNode node) {
    if (path.equals(DocumentConstants.Fields.DOC_ID)) {
      return DocumentId.fromJson(node);
    }
    return jsonNodeValue(node);
  }

  private static Object jsonNodeValue(JsonNode node) {
    switch (node.getNodeType()) {
      case BOOLEAN:
        return node.booleanValue();
      case NUMBER:
        return node.decimalValue();
      case STRING:
        return node.textValue();
      case NULL:
        return null;
      case ARRAY:
        {
          ArrayNode arrayNode = (ArrayNode) node;
          List<Object> arrayVals = new ArrayList<>(arrayNode.size());
          for (JsonNode element : arrayNode) {
            arrayVals.add(jsonNodeValue(element));
          }
          return arrayVals;
        }
      case OBJECT:
        {
          ObjectNode objectNode = (ObjectNode) node;
          Map<String, Object> values = new LinkedHashMap<>(objectNode.size());
          final Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
          while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> nextField = fields.next();
            values.put(nextField.getKey(), jsonNodeValue(nextField.getValue()));
          }
          return values;
        }
      default:
        throw new JsonApiException(
            ErrorCode.UNSUPPORTED_FILTER_DATA_TYPE,
            String.format("Unsupported NodeType %s", node.getNodeType()));
    }
  }
}
