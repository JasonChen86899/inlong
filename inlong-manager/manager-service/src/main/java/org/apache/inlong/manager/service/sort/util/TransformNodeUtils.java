/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.manager.service.sort.util;

import com.google.common.collect.Lists;
import org.apache.commons.collections.CollectionUtils;
import org.apache.inlong.manager.common.enums.TransformType;
import org.apache.inlong.manager.common.pojo.stream.StreamField;
import org.apache.inlong.manager.common.pojo.transform.TransformDefinition;
import org.apache.inlong.manager.common.pojo.transform.TransformResponse;
import org.apache.inlong.manager.common.pojo.transform.deduplication.DeDuplicationDefinition;
import org.apache.inlong.manager.common.pojo.transform.deduplication.DeDuplicationDefinition.DeDuplicationStrategy;
import org.apache.inlong.manager.common.util.StreamParseUtils;
import org.apache.inlong.sort.protocol.FieldInfo;
import org.apache.inlong.sort.protocol.node.transform.DistinctNode;
import org.apache.inlong.sort.protocol.node.transform.TransformNode;
import org.apache.inlong.sort.protocol.transformation.OrderDirection;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Parse TransformResponse to TransformNode which sort needed
 */
public class TransformNodeUtils {

    public static List<TransformNode> createTransformNodes(List<TransformResponse> transformResponses) {
        if (CollectionUtils.isEmpty(transformResponses)) {
            return Lists.newArrayList();
        }
        List<TransformNode> transformNodes = transformResponses.stream()
                .map(transformResponse -> createTransformNode(transformResponse)).collect(Collectors.toList());
        return transformNodes;
    }

    public static TransformNode createTransformNode(TransformResponse transformResponse) {
        TransformType transformType = TransformType.forType(transformResponse.getTransformType());
        if (transformType == TransformType.DE_DUPLICATION) {
            TransformDefinition transformDefinition = StreamParseUtils.parseTransformDefinition(
                    transformResponse.getTransformDefinition(), transformType);
            return createDistinctNode((DeDuplicationDefinition) transformDefinition, transformResponse);
        } else {
            return createNormalTransformNode(transformResponse);
        }
    }

    /**
     * Create distinct node based on deDuplicationDefinition
     *
     * @param deDuplicationDefinition
     * @param transformResponse
     * @return
     */
    public static DistinctNode createDistinctNode(DeDuplicationDefinition deDuplicationDefinition,
            TransformResponse transformResponse) {
        String transformName = transformResponse.getTransformName();
        List<StreamField> streamFields = deDuplicationDefinition.getDupFields();
        List<FieldInfo> distinctFields = streamFields.stream()
                .map(streamField -> new FieldInfo(streamField.getFieldName(), transformName,
                        FieldInfoUtils.convertFieldFormat(streamField.getFieldType().name(),
                                streamField.getFieldFormat())))
                .collect(Collectors.toList());
        StreamField timingField = deDuplicationDefinition.getTimingField();
        FieldInfo orderField = new FieldInfo(timingField.getFieldName(), transformName,
                FieldInfoUtils.convertFieldFormat(timingField.getFieldType().name(), timingField.getFieldFormat()));
        DeDuplicationStrategy deDuplicationStrategy = deDuplicationDefinition.getDeDuplicationStrategy();
        OrderDirection orderDirection = null;
        switch (deDuplicationStrategy) {
            case RESERVE_LAST:
                orderDirection = OrderDirection.DESC;
                break;
            case RESERVE_FIRST:
                orderDirection = OrderDirection.ASC;
                break;
            default:
                throw new UnsupportedOperationException(
                        String.format("Unsupported deduplication strategy=%s for inlong", deDuplicationStrategy));
        }
        TransformNode transformNode = createTransformNode(transformResponse);
        return new DistinctNode(transformNode.getId(),
                transformNode.getName(),
                transformNode.getFields(),
                transformNode.getFieldRelationShips(),
                transformNode.getFilters(),
                distinctFields,
                orderField,
                orderDirection);

    }

    /**
     * Create transform node based on transformResponse
     *
     * @param transformResponse
     * @return
     */
    public static TransformNode createNormalTransformNode(TransformResponse transformResponse) {
        TransformNode transformNode = new TransformNode();
        transformNode.setId(transformResponse.getTransformName());
        transformNode.setName(transformResponse.getTransformName());
        List<FieldInfo> fieldInfos = transformResponse.getFieldList().stream().map(streamFieldInfo -> {
            String transformName = transformResponse.getTransformName();
            String fieldType = streamFieldInfo.getFieldType();
            String fieldFormat = streamFieldInfo.getFieldFormat();
            String fieldName = streamFieldInfo.getFieldName();
            FieldInfo fieldInfo = new FieldInfo(fieldName, transformName,
                    FieldInfoUtils.convertFieldFormat(fieldType, fieldFormat));
            return fieldInfo;
        }).collect(Collectors.toList());
        transformNode.setFields(fieldInfos);
        transformNode.setFieldRelationShips(FieldRelationShipUtils.createFieldRelationShips(transformResponse));
        transformNode.setFilters(
                FilterFunctionUtils.createFilterFunctions(transformResponse));
        return transformNode;
    }
}