/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch5x.cache;

import java.util.*;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.register.*;
import org.apache.skywalking.oap.server.core.storage.cache.IServiceInventoryCacheDAO;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch5x.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch5x.client.ElasticSearchClient5x;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class ServiceInventoryCacheEsDAO extends EsDAO implements IServiceInventoryCacheDAO {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInventoryCacheEsDAO.class);

    private final ServiceInventory.Builder builder = new ServiceInventory.Builder();

    public ServiceInventoryCacheEsDAO(ElasticSearchClient5x client) {
        super(client);
    }

    @Override public int getServiceId(String serviceName) {
        String id = ServiceInventory.buildId(serviceName);
        return get(id);
    }

    @Override public int getServiceId(int addressId) {
        String id = ServiceInventory.buildId(addressId);
        return get(id);
    }

    private int get(String id) {
        try {
            GetResponse response = getClient().get(ServiceInventory.MODEL_NAME, id);
            if (response.isExists()) {
                return (int)response.getSource().getOrDefault(RegisterSource.SEQUENCE, 0);
            } else {
                return Const.NONE;
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
            return Const.NONE;
        }
    }

    @Override public ServiceInventory get(int serviceId) {
        try {
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.termQuery(ServiceInventory.SEQUENCE, serviceId));
            searchSourceBuilder.size(1);

            SearchResponse response = getClient().search(ServiceInventory.MODEL_NAME, searchSourceBuilder);
            if (response.getHits().totalHits == 1) {
                SearchHit searchHit = response.getHits().getAt(0);
                return builder.map2Data(searchHit.getSourceAsMap());
            } else {
                return null;
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
            return null;
        }
    }

    @Override public List<ServiceInventory> loadLastMappingUpdate() {
        List<ServiceInventory> serviceInventories = new ArrayList<>();

        try {
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
            boolQuery.must().add(QueryBuilders.termQuery(ServiceInventory.IS_ADDRESS, BooleanUtils.TRUE));
            boolQuery.must().add(QueryBuilders.rangeQuery(ServiceInventory.MAPPING_LAST_UPDATE_TIME).gte(System.currentTimeMillis() - 10000));

            searchSourceBuilder.query(boolQuery);
            searchSourceBuilder.size(50);

            SearchResponse response = getClient().search(ServiceInventory.MODEL_NAME, searchSourceBuilder);

            for (SearchHit searchHit : response.getHits().getHits()) {
                serviceInventories.add(this.builder.map2Data(searchHit.getSourceAsMap()));
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }

        return serviceInventories;
    }
}
