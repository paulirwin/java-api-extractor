package io.github.paulirwin.javaapiextractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonSchemaValidationTest {
    private static ObjectMapper objectMapper;
    private static JsonNode schemaNode;

    @BeforeAll
    static void loadSchema() throws IOException {
        objectMapper = new ObjectMapper();
        try (InputStream schemaStream = JsonSchemaValidationTest.class.getResourceAsStream("/api-schema.json")) {
            assertNotNull(schemaStream, "Schema file not found in resources");
            schemaNode = objectMapper.readTree(schemaStream);
        }
    }

    @Test
    void schemaIsValid() {
        assertNotNull(schemaNode, "Schema node should be loaded");
        assertTrue(schemaNode.has("$schema"), "Schema should have $schema property");
        assertTrue(schemaNode.has("definitions"), "Schema should have definitions");
        assertEquals("array", schemaNode.get("type").asText(), "Root schema should be an array");
    }

    @Test
    void schemaHasRequiredDefinitions() {
        JsonNode definitions = schemaNode.get("definitions");
        assertTrue(definitions.has("LibraryResult"), "Schema should define LibraryResult");
        assertTrue(definitions.has("TypeMetadata"), "Schema should define TypeMetadata");
        assertTrue(definitions.has("MethodMetadata"), "Schema should define MethodMetadata");
        assertTrue(definitions.has("FieldMetadata"), "Schema should define FieldMetadata");
        assertTrue(definitions.has("ConstructorMetadata"), "Schema should define ConstructorMetadata");
        assertTrue(definitions.has("AnnotationMetadata"), "Schema should define AnnotationMetadata");
        assertTrue(definitions.has("ParameterMetadata"), "Schema should define ParameterMetadata");
        assertTrue(definitions.has("EnumConstantMetadata"), "Schema should define EnumConstantMetadata");
        assertTrue(definitions.has("ConstantValue"), "Schema should define ConstantValue");
        assertTrue(definitions.has("AnnotationValue"), "Schema should define AnnotationValue");
    }

    @Test
    void emptyArrayIsValid() throws IOException {
        String json = "[]";
        JsonNode node = objectMapper.readTree(json);
        assertTrue(node.isArray(), "Parsed JSON should be array");
        assertEquals(0, node.size(), "Empty array should have size 0");
    }

    @Test
    void libraryResultStructureIsValid() throws IOException {
        String json = """
                [
                  {
                    "library": {
                      "groupId": "com.example",
                      "artifactId": "my-lib",
                      "version": "1.0.0"
                    },
                    "types": []
                  }
                ]
                """;
        JsonNode node = objectMapper.readTree(json);
        assertTrue(node.isArray(), "Parsed JSON should be array");
        assertTrue(node.get(0).has("library"), "LibraryResult should have library");
        assertTrue(node.get(0).has("types"), "LibraryResult should have types");
        assertTrue(node.get(0).get("types").isArray(), "types should be array");
    }

    @Test
    void typeMetadataStructureIsValid() throws IOException {
        String json = """
                [
                  {
                    "library": {
                      "groupId": "com.example",
                      "artifactId": "my-lib",
                      "version": "1.0.0"
                    },
                    "types": [
                      {
                        "packageName": "com.example",
                        "kind": "class",
                        "name": "MyClass",
                        "fullName": "com.example.MyClass",
                        "modifiers": ["public"],
                        "interfaces": [],
                        "genericInterfaces": [],
                        "typeParameters": [],
                        "annotations": [],
                        "constructors": [],
                        "methods": [],
                        "enumConstants": [],
                        "fields": []
                      }
                    ]
                  }
                ]
                """;
        JsonNode node = objectMapper.readTree(json);
        JsonNode type = node.get(0).get("types").get(0);
        assertTrue(type.has("packageName"), "TypeMetadata should have packageName");
        assertTrue(type.has("kind"), "TypeMetadata should have kind");
        assertTrue(type.has("name"), "TypeMetadata should have name");
        assertTrue(type.has("fullName"), "TypeMetadata should have fullName");
        assertTrue(type.has("modifiers"), "TypeMetadata should have modifiers");
        assertEquals("class", type.get("kind").asText(), "kind should be 'class'");
    }

    @Test
    void validKindValues() throws IOException {
        List<String> validKinds = List.of("class", "interface", "enum", "record", "annotation");
        JsonNode definitions = schemaNode.get("definitions");
        JsonNode typeMetadata = definitions.get("TypeMetadata");
        JsonNode kindProperty = typeMetadata.get("properties").get("kind");

        for (String validKind : validKinds) {
            assertTrue(kindProperty.get("enum").toString().contains(validKind),
                    "kind enum should include: " + validKind);
        }
    }

    @Test
    void methodMetadataCanBeParsed() throws IOException {
        String json = """
                [
                  {
                    "library": {
                      "groupId": "com.example",
                      "artifactId": "my-lib",
                      "version": "1.0.0"
                    },
                    "types": [
                      {
                        "packageName": "com.example",
                        "kind": "class",
                        "name": "MyClass",
                        "fullName": "com.example.MyClass",
                        "modifiers": ["public"],
                        "interfaces": [],
                        "genericInterfaces": [],
                        "typeParameters": [],
                        "annotations": [],
                        "constructors": [],
                        "methods": [
                          {
                            "name": "getValue",
                            "returnType": "int",
                            "parameters": [],
                            "modifiers": ["public"],
                            "typeParameters": [],
                            "throwsTypes": [],
                            "annotations": [],
                            "isVarArgs": false
                          }
                        ],
                        "enumConstants": [],
                        "fields": []
                      }
                    ]
                  }
                ]
                """;
        JsonNode node = objectMapper.readTree(json);
        JsonNode method = node.get(0).get("types").get(0).get("methods").get(0);
        assertEquals("getValue", method.get("name").asText());
        assertEquals("int", method.get("returnType").asText());
        assertTrue(method.get("parameters").isArray());
        assertTrue(method.get("modifiers").isArray());
    }

    @Test
    void annotationValueTypesAreDefined() {
        JsonNode definitions = schemaNode.get("definitions");
        assertTrue(definitions.has("BooleanAnnotationValue"));
        assertTrue(definitions.has("IntAnnotationValue"));
        assertTrue(definitions.has("StringAnnotationValue"));
        assertTrue(definitions.has("ClassAnnotationValue"));
        assertTrue(definitions.has("EnumAnnotationValue"));
        assertTrue(definitions.has("ArrayAnnotationValue"));
        assertTrue(definitions.has("AnnotationValueRef"));
    }

    @Test
    void constantValueTypesAreDefined() {
        JsonNode definitions = schemaNode.get("definitions");
        assertTrue(definitions.has("BooleanConstantValue"));
        assertTrue(definitions.has("IntConstantValue"));
        assertTrue(definitions.has("LongConstantValue"));
        assertTrue(definitions.has("StringConstantValue"));
        assertTrue(definitions.has("CharConstantValue"));
    }

    @Test
    void enumConstantMetadataCanBeParsed() throws IOException {
        String json = """
                [
                  {
                    "library": {
                      "groupId": "com.example",
                      "artifactId": "my-lib",
                      "version": "1.0.0"
                    },
                    "types": [
                      {
                        "packageName": "com.example",
                        "kind": "enum",
                        "name": "Color",
                        "fullName": "com.example.Color",
                        "modifiers": ["public"],
                        "interfaces": [],
                        "genericInterfaces": [],
                        "typeParameters": [],
                        "annotations": [],
                        "constructors": [],
                        "methods": [],
                        "enumConstants": [
                          {
                            "name": "RED",
                            "annotations": []
                          },
                          {
                            "name": "GREEN",
                            "annotations": []
                          }
                        ],
                        "fields": []
                      }
                    ]
                  }
                ]
                """;
        JsonNode node = objectMapper.readTree(json);
        JsonNode enumConstants = node.get(0).get("types").get(0).get("enumConstants");
        assertEquals(2, enumConstants.size());
        assertEquals("RED", enumConstants.get(0).get("name").asText());
        assertEquals("GREEN", enumConstants.get(1).get("name").asText());
    }

    @Test
    void fieldWithConstantValueCanBeParsed() throws IOException {
        String json = """
                [
                  {
                    "library": {
                      "groupId": "com.example",
                      "artifactId": "my-lib",
                      "version": "1.0.0"
                    },
                    "types": [
                      {
                        "packageName": "com.example",
                        "kind": "class",
                        "name": "MyClass",
                        "fullName": "com.example.MyClass",
                        "modifiers": ["public"],
                        "interfaces": [],
                        "genericInterfaces": [],
                        "typeParameters": [],
                        "annotations": [],
                        "constructors": [],
                        "methods": [],
                        "enumConstants": [],
                        "fields": [
                          {
                            "name": "MAX_SIZE",
                            "type": "int",
                            "modifiers": ["public", "static", "final"],
                            "annotations": [],
                            "isStatic": true,
                            "constantValue": {
                              "kind": "int",
                              "value": 100
                            }
                          }
                        ]
                      }
                    ]
                  }
                ]
                """;
        JsonNode node = objectMapper.readTree(json);
        JsonNode field = node.get(0).get("types").get(0).get("fields").get(0);
        assertEquals("MAX_SIZE", field.get("name").asText());
        assertTrue(field.has("constantValue"));
        assertEquals("int", field.get("constantValue").get("kind").asText());
        assertEquals(100, field.get("constantValue").get("value").asInt());
    }

    @Test
    void annotationWithMultipleArgumentTypesCanBeParsed() throws IOException {
        String json = """
                [
                  {
                    "library": {
                      "groupId": "com.example",
                      "artifactId": "my-lib",
                      "version": "1.0.0"
                    },
                    "types": [
                      {
                        "packageName": "com.example",
                        "kind": "class",
                        "name": "MyClass",
                        "fullName": "com.example.MyClass",
                        "modifiers": ["public"],
                        "interfaces": [],
                        "genericInterfaces": [],
                        "typeParameters": [],
                        "annotations": [
                          {
                            "type": "java.lang.Deprecated",
                            "arguments": [
                              {
                                "name": "since",
                                "value": {
                                  "kind": "string",
                                  "value": "2.0"
                                }
                              },
                              {
                                "name": "forRemoval",
                                "value": {
                                  "kind": "boolean",
                                  "value": true
                                }
                              }
                            ]
                          }
                        ],
                        "constructors": [],
                        "methods": [],
                        "enumConstants": [],
                        "fields": []
                      }
                    ]
                  }
                ]
                """;
        JsonNode node = objectMapper.readTree(json);
        JsonNode annotation = node.get(0).get("types").get(0).get("annotations").get(0);
        assertEquals("java.lang.Deprecated", annotation.get("type").asText());
        assertEquals(2, annotation.get("arguments").size());

        JsonNode sinceArg = annotation.get("arguments").get(0);
        assertEquals("since", sinceArg.get("name").asText());
        assertEquals("string", sinceArg.get("value").get("kind").asText());
        assertEquals("2.0", sinceArg.get("value").get("value").asText());
    }
}
