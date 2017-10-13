/*
 * Copyright 2010-2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.codegen.poet.model;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.security.InvalidParameterException;
import java.util.Iterator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import software.amazon.awssdk.codegen.model.intermediate.IntermediateModel;
import software.amazon.awssdk.codegen.model.intermediate.MemberModel;
import software.amazon.awssdk.codegen.model.intermediate.OperationModel;
import software.amazon.awssdk.codegen.model.intermediate.ShapeModel;
import software.amazon.awssdk.codegen.model.service.PaginatorDefinition;
import software.amazon.awssdk.codegen.poet.ClassSpec;
import software.amazon.awssdk.codegen.poet.PoetExtensions;
import software.amazon.awssdk.codegen.poet.PoetUtils;
import software.amazon.awssdk.pagination.Paginated;
import software.amazon.awssdk.pagination.PaginatedItemsIterable;
import software.amazon.awssdk.pagination.PaginatedResponsesIterator;
import software.amazon.awssdk.pagination.SdkIterable;

/**
 * Java poet {@link ClassSpec} to generate the response class for sync paginated operations.
 *
 * Sample of a generated class with annotations:

    public final class ListTablesPaginator implements Paginated&lt;ListTablesResponse, String> {
        private final DynamoDBClient client;

        private final ListTablesRequest firstRequest;

        private final ListTablesResponse firstResponsePage;

        public ListTablesPaginator(final DynamoDBClient client, final ListTablesRequest firstRequest,
            final ListTablesResponse firstResponsePage) {
            this.client = client;
            this.firstRequest = firstRequest;
            this.firstResponsePage = firstResponsePage;
        }

        public ListTablesResponse firstPage() {
            return firstResponsePage;
        }

        public Iterator&lt;ListTablesResponse> iterator() {
            Predicate&lt;ListTablesResponse> hasNextResponse = response -> response != null;

            Function&lt;ListTablesResponse, ListTablesResponse> getNextResponse = response -> {
                if (response == null || response.lastEvaluatedTableName() == null) {
                    return null;
                } else {
                    return client.listTables(firstRequest.toBuilder().exclusiveStartTableName(response.lastEvaluatedTableName())
                                .build());
                 }
            };

            return new PaginatedResponsesIterable(firstResponsePage, getNextResponse).iterator();
        }

        public SdkIterable&lt;String> allItems() {
            Function&lt;ListTablesResponse, Iterator&lt;String>> getPaginatedMemberIterator
                        = response -> response != null ? response.tableNames().iterator() : null;

            return new PaginatedItemsIterable(iterator(), getPaginatedMemberIterator);
        }

        public SdkIterable&lt;String> tableNames() {
            return allItems();
        }
    }
 */
public class PaginatorResponseClassSpec implements ClassSpec {

    private static final String CLIENT_MEMBER = "client";
    private static final String REQUEST_MEMBER = "firstRequest";
    private static final String RESPONSE_MEMBER = "firstResponsePage";
    private static final String ALL_ITEMS_METHOD = "allItems";

    private final IntermediateModel model;
    private final PoetExtensions poetExtensions;
    private final TypeProvider typeProvider;
    private final String c2jOperationName;
    private final PaginatorDefinition paginatorDefinition;
    private final OperationModel operationModel;

    public PaginatorResponseClassSpec(IntermediateModel intermediateModel,
                                      String c2jOperationName,
                                      PaginatorDefinition paginatorDefinition) {

        this.model = intermediateModel;
        this.poetExtensions = new PoetExtensions(intermediateModel);
        this.typeProvider = new TypeProvider(intermediateModel);
        this.c2jOperationName = c2jOperationName;
        this.paginatorDefinition = paginatorDefinition;
        this.operationModel = model.getOperation(c2jOperationName);
    }

    @Override
    public TypeSpec poetSpec() {
        TypeSpec.Builder specBuilder = TypeSpec.classBuilder(className())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(PoetUtils.GENERATED)
                .addSuperinterface(getPaginatedInterface())
                .addFields(Stream.of(syncClientInterfaceField(), requestClassField(), responseClassField())
                                 .collect(Collectors.toList()))
                .addMethod(constructor())
                .addMethod(firstPageMethod())
                .addMethod(iteratorMethod())
                .addMethod(allItemsMethod())
                .addMethod(methodSimilarToPaginatedMemberName());

        return specBuilder.build();
    }

    @Override
    public ClassName className() {
        return poetExtensions.getResponseClassForPaginatedOperation(c2jOperationName);
    }

    /**
     * Returns the interface that is implemented by the Paginated Response class.
     */
    private TypeName getPaginatedInterface() {
        return ParameterizedTypeName.get(ClassName.get(Paginated.class), responseType(), getPaginatedMemberType());
    }

    /**
     * @return A Poet {@link ClassName} for the sync operation request type.
     *
     * Example: For ListTables operation, it will be "ListTablesRequest" class.
     */
    private ClassName requestType() {
        return poetExtensions.getModelClass(operationModel.getInput().getVariableType());
    }

    /**
     * @return A Poet {@link ClassName} for the sync operation response type.
     *
     * Example: For ListTables operation, it will be "ListTablesResponse" class.
     */
    private ClassName responseType() {
        return poetExtensions.getModelClass(operationModel.getReturnType().getReturnType());
    }

    /**
     * @return A Poet {@link ClassName} for the sync client interface
     */
    private ClassName getClientInterfaceName() {
        return poetExtensions.getClientClass(model.getMetadata().getSyncInterface());
    }

    private FieldSpec syncClientInterfaceField() {
        return FieldSpec.builder(getClientInterfaceName(), CLIENT_MEMBER, Modifier.PRIVATE, Modifier.FINAL).build();
    }

    private FieldSpec requestClassField() {
        return FieldSpec.builder(requestType(), REQUEST_MEMBER, Modifier.PRIVATE, Modifier.FINAL).build();
    }

    private FieldSpec responseClassField() {
        return FieldSpec.builder(responseType(), RESPONSE_MEMBER, Modifier.PRIVATE, Modifier.FINAL).build();
    }

    private MethodSpec constructor() {
        return MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(getClientInterfaceName(), CLIENT_MEMBER, Modifier.FINAL)
                .addParameter(requestType(), REQUEST_MEMBER, Modifier.FINAL)
                .addParameter(responseType(), RESPONSE_MEMBER, Modifier.FINAL)
                .addStatement("this.$L = $L", CLIENT_MEMBER, CLIENT_MEMBER)
                .addStatement("this.$L = $L", REQUEST_MEMBER, REQUEST_MEMBER)
                .addStatement("this.$L = $L", RESPONSE_MEMBER, RESPONSE_MEMBER)
                .build();
    }

    /**
     * A {@link MethodSpec} for the firstPage() method which returns the
     * first response page for the paginated operation.
     */
    private MethodSpec firstPageMethod() {
        return MethodSpec.methodBuilder("firstPage")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(responseType())
                .addStatement("return $L", RESPONSE_MEMBER)
                .build();
    }

    /**
     * A {@link MethodSpec} for the overridden iterator() method which is inherited
     * from the interface.
     */
    private MethodSpec iteratorMethod() {
        return MethodSpec.methodBuilder("iterator")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(ClassName.get(Iterator.class), responseType()))
                .addCode("\n")
                .addCode(getNextResponseBlock())
                .addCode("\n")
                .addStatement("return new $T($L, $L)", PaginatedResponsesIterator.class,
                        RESPONSE_MEMBER, "getNextResponse")
                .build();
    }

    /*
     * Returns {@link CodeBlock} representing a {@link Function} that takes a response as input
     * and returns the next response as output.
     *
     * A sample from dynamodb listTables paginator:
     *
     *  Function<ListTablesResponse, ListTablesResponse> getNextResponse = response -> {
     *      if (response == null || response.lastEvaluatedTableName() == null) {
     *          return null;
     *      } else {
     *          return client.listTables(firstRequest.toBuilder()
     *          .exclusiveStartTableName(response.lastEvaluatedTableName())
     *          .build());
     *      }
     *  };
     *
     */
    private CodeBlock getNextResponseBlock() {
        CodeBlock ifElseBlock = CodeBlock.builder()
                .beginControlFlow("if (response == null || response.$L == null)", fluentGetterMethodForOutputToken())
                .addStatement("return null")
                .nextControlFlow("else")
                .addStatement("return client.$L(firstRequest.toBuilder().$L(response.$L).build())",
                        operationModel.getMethodName(), fluentSetterNameForInputToken(), fluentGetterMethodForOutputToken())
                .endControlFlow()
                .build();

        return CodeBlock.builder()
                .add("$T getNextResponse = response -> {\n",
                        ParameterizedTypeName.get(ClassName.get(Function.class), responseType(), responseType()))
                .add(ifElseBlock)
                .addStatement("}")
                .build();
    }

    /**
     * @return the fluent setter method name for {@link PaginatorDefinition#getInputToken()}
     * member in the request.
     */
    private String fluentSetterNameForInputToken() {
        return operationModel.getInputShape()
                .findMemberModelByC2jName(paginatorDefinition.getInputToken())
                .getFluentSetterMethodName();
    }

    /**
     * @return the fluent getter method for {@link PaginatorDefinition#getOutputToken()}
     * member in the response. The returned String includes the '()' after each method name.
     *
     * The {@link PaginatorDefinition#getOutputToken()} can be a nested String.
     * An example would be StreamDescription.LastEvaluatedShardId which represents LastEvaluatedShardId member
     * in StreamDescription class. The return String for it would be "streamDescription().lastEvaluatedShardId()"
     */
    private String fluentGetterMethodForOutputToken() {
        final String[] hierarchy = paginatorDefinition.getOutputToken().split("\\.");

        if (hierarchy.length < 1) {
            throw new IllegalArgumentException("Error when splitting output token for " + c2jOperationName + " paginator.");
        }

        ShapeModel parentShape = operationModel.getOutputShape();
        final StringBuilder getterMethod = new StringBuilder();

        for (int i = 0; i < hierarchy.length; i++) {
            getterMethod.append(".")
                    .append(parentShape.findMemberModelByC2jName(hierarchy[i]).getFluentGetterMethodName())
                    .append("()");

            parentShape =  parentShape.findMemberModelByC2jName(hierarchy[i]).getShape();
        }

        return getterMethod.substring(1);
    }

    /**
     * @return the fluent getter method for {@link PaginatorDefinition#getResultKey()} ()}
     * member in the response. The returned String includes the '()' after each method name.
     *
     * The {@link PaginatorDefinition#getResultKey()} can be a nested String.
     * An example would be StreamDescription.Shards which represents Shards member in StreamDescription class.
     * The return String for it would be "streamDescription().shards()"
     */
    private String fluentGetterMethodForResultKey() {
        final String[] hierarchy = paginatorDefinition.getResultKey().split("\\.");

        if (hierarchy.length < 1) {
            throw new IllegalArgumentException("Error when splitting result key for " + c2jOperationName + " paginator.");
        }

        ShapeModel parentShape = operationModel.getOutputShape();
        final StringBuilder getterMethod = new StringBuilder();

        for (int i = 0; i < hierarchy.length; i++) {
            getterMethod.append(".")
                    .append(parentShape.findMemberModelByC2jName(hierarchy[i]).getFluentGetterMethodName())
                    .append("()");

            parentShape =  parentShape.findMemberModelByC2jName(hierarchy[i]).getShape();
        }

        return getterMethod.substring(1);
    }

    /**
     * @return The {@link MemberModel} of the {@link PaginatorDefinition#getResultKey()}. If result key is nested,
     * then returns the member model of the last child shape.
     */
    private MemberModel memberModelForResultKey() {
        final String[] hierarchy = paginatorDefinition.getResultKey().split("\\.");

        if (hierarchy.length < 1) {
            throw new IllegalArgumentException("Error when splitting result key for " + c2jOperationName + " paginator.");
        }

        ShapeModel shape = operationModel.getOutputShape();

        for (int i = 0; i < hierarchy.length - 1; i++) {
            shape = shape.findMemberModelByC2jName(hierarchy[i]).getShape();
        }

        return shape.getMemberByC2jName(hierarchy[hierarchy.length - 1]);
    }

    /*
     * Returns a {@link MethodSpec} for the overridden #ALL_ITEMS_METHOD method that returns
     * an iterable for iterating through the paginated items across responses.
     *
     *  A sample from dynamodb listTables paginator:
     *
     *  public SdkIterable<String> allItems() {
     *     Function<ListTablesResponse, Iterator<String>> getPaginatedItemIterator =
     *              response -> response != null ? response.tableNames().iterator() : null;
     *
     *     return new PaginatedItemsIterable(this, getPaginatedItemIterator);
     *  }
     *
     */
    private MethodSpec allItemsMethod() {
        return MethodSpec.methodBuilder("allItems")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(ClassName.get(SdkIterable.class), getPaginatedMemberType()))
                .addCode("$T getPaginatedMemberIterator = ",
                        ParameterizedTypeName.get(ClassName.get(Function.class), responseType(),
                                ParameterizedTypeName.get(ClassName.get(Iterator.class), getPaginatedMemberType())))
                .addCode(getPaginatedMemberIteratorLambdaBlock())
                .addCode("\n")
                .addStatement("return new $T(this, getPaginatedMemberIterator)", PaginatedItemsIterable.class)
                .build();
    }

    private CodeBlock getPaginatedMemberIteratorLambdaBlock() {
        MemberModel resultKeyModel = memberModelForResultKey();

        CodeBlock iteratorBlock = null;

        if (resultKeyModel.isList()) {
            iteratorBlock = CodeBlock.builder().add("response.$L.iterator()", fluentGetterMethodForResultKey())
                    .build();

        } else if (resultKeyModel.isMap()) {
            iteratorBlock = CodeBlock.builder().add("response.$L.entrySet().iterator()", fluentGetterMethodForResultKey())
                    .build();
        }

        return CodeBlock.builder().addStatement("response -> response != null ? $L : null", iteratorBlock).build();
    }

    /*
     * Returns a {@link MethodSpec} for the convenience method whose name is based on
     * the paginated member. This gives clarity for the user when working with paginated members
     * of primitive types.
     *
     * For example, the paginated member in dynamodb listTables operation is "tableNames" which is list of Strings.
     * It is not clear what the member is when using the allItems() method which returns SdkIterable<String>.
     * So this convenient method is named tableNames() to improve clarity.
     */
    private MethodSpec methodSimilarToPaginatedMemberName() {
        return MethodSpec.methodBuilder(memberModelForResultKey().getFluentGetterMethodName())
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(ClassName.get(SdkIterable.class), getPaginatedMemberType()))
                .addStatement("return $L()", ALL_ITEMS_METHOD)
                .build();
    }

    /*
     * Returns a {@link TypeName} of the paginated member in the response.
     *
     * Examples:
     * If paginated item is represented as List<String>, then member type is String.
     * If paginated item is represented as List<Foo>, then member type is Foo.
     * If paginated item is represented as Map<String, List<Foo>>,
     *              then member type is Map.Entry<String, List<Foo>>.
     */
    private TypeName getPaginatedMemberType() {

        MemberModel resultKeyModel = memberModelForResultKey();

        if (resultKeyModel == null) {
            throw new InvalidParameterException("MemberModel is not found for result key: " + paginatorDefinition.getResultKey());
        }

        if (resultKeyModel.isList()) {
            return typeProvider.fieldType(resultKeyModel.getListModel().getListMemberModel());

        } else if (resultKeyModel.isMap()) {
            return typeProvider.mapEntryTypeWithConcreteTypes(resultKeyModel.getMapModel());
        } else {
            throw new IllegalArgumentException("Result key for " + c2jOperationName +
                    " operation in paginators file should be either a list or a map.");
        }
    }

}
