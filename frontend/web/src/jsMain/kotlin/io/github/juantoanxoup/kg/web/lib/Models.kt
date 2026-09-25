package io.github.juantoanxoup.kg.web.lib

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response models of the backend API, field names as the server sends them. */

@Serializable
data class GraphStatistics(
    @SerialName("total_triples") val totalTriples: Int = 0,
    @SerialName("unique_subjects") val uniqueSubjects: Int = 0,
    @SerialName("unique_predicates") val uniquePredicates: Int = 0,
    @SerialName("unique_objects") val uniqueObjects: Int = 0,
)

@Serializable
data class GraphSummary(
    @SerialName("graph_id") val graphId: String,
    val filename: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("entities_count") val entitiesCount: Int,
    @SerialName("relations_count") val relationsCount: Int,
    val statistics: GraphStatistics? = null,
)

@Serializable
data class GraphsResponse(
    val graphs: List<GraphSummary> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class GraphInfo(
    @SerialName("graph_id") val graphId: String,
    val filename: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("entities_count") val entitiesCount: Int,
    @SerialName("relations_count") val relationsCount: Int,
    val statistics: GraphStatistics? = null,
    @SerialName("sample_entities") val sampleEntities: List<String> = emptyList(),
)

@Serializable
data class GraphCreationResponse(
    @SerialName("graph_id") val graphId: String,
    val message: String,
    @SerialName("entities_count") val entitiesCount: Int,
    @SerialName("relations_count") val relationsCount: Int,
    val statistics: GraphStatistics? = null,
)

@Serializable
data class Entity(
    val text: String,
    val type: String,
)

@Serializable
data class EntitiesResponse(
    val entities: List<Entity> = emptyList(),
    val count: Int = 0,
)

@Serializable
data class SearchResult(
    val subject: String,
    val predicate: String,
    @SerialName("object") val obj: String,
    val text: String,
    val similarity: Double,
)

@Serializable
data class SemanticSearchRequest(
    @SerialName("graph_id") val graphId: String,
    val query: String,
    @SerialName("top_k") val topK: Int = 5,
)

@Serializable
data class SemanticSearchResponse(
    val results: List<SearchResult> = emptyList(),
    val query: String = "",
)

@Serializable
data class QuestionAnswerRequest(
    @SerialName("graph_id") val graphId: String,
    val question: String,
)

@Serializable
data class QuestionAnswerResponse(
    val question: String,
    val answer: String,
    @SerialName("relevant_facts") val relevantFacts: List<SearchResult> = emptyList(),
)

@Serializable
data class EntityRelationsRequest(
    @SerialName("graph_id") val graphId: String,
    @SerialName("entity_name") val entityName: String,
)

@Serializable
data class EntityRelationsResponse(
    val entity: String,
    val relations: List<Map<String, String>> = emptyList(),
    val count: Int = 0,
)

@Serializable
data class SparqlQueryRequest(
    @SerialName("graph_id") val graphId: String,
    val query: String,
)

@Serializable
data class SparqlQueryResponse(
    val results: List<Map<String, String>> = emptyList(),
    val count: Int = 0,
)

@Serializable
data class ChatHistoryMessage(
    val role: String,
    val content: String,
    val timestamp: String,
    val facts: List<String>? = null,
)

/** Reply to `POST /chat_history/{id}`. */
@Serializable
data class ChatSavedResponse(
    val message: String,
    @SerialName("total_messages") val totalMessages: Int,
)

@Serializable
data class ChatHistoryResponse(
    @SerialName("graph_id") val graphId: String,
    val messages: List<ChatHistoryMessage> = emptyList(),
    val count: Int = 0,
)

@Serializable
data class MessageResponse(
    val message: String,
)
