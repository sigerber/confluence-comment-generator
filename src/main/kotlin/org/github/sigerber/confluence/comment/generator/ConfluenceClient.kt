package org.github.sigerber.confluence.comment.generator

import com.beust.klaxon.*
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.map
import java.io.InputStream
import java.nio.charset.Charset

class ConfluenceClient(host: String, private val username: String, private val password: String) {

    private val basePath = "https://$host/rest/api"
    var commentFooter = ""

    fun fetchPageOrParentPageOfComment(commentOrPageId: Long): Pair<JsonObject, String> {
        val page = fetchPageObject(commentOrPageId)
        val pageType = page.string("type")!!

        if (pageType == "comment") {
            return Pair(fetchPageContainingComment(commentOrPageId), pageType)
        } else {
            return Pair(page, pageType)
        }
    }

    private fun fetchPageObject(pageId: Long): JsonObject {
        val (_, _, result) = "$basePath/content/$pageId"
                .httpGet(listOf("expand" to "body.view"))
                .authenticate(username, password)
                .responseObject(JsonDeserializer())

        when (result) {
            is Result.Failure -> {
                throw RuntimeException("Well, that didn't work. \n ${result.error.response}")
            }
            is Result.Success -> {
                return result.value
            }
        }
    }

    private fun fetchPageContainingComment(replyToId: Long): JsonObject {
        val (_, _, result) = "$basePath/content/$replyToId"
                .httpGet(listOf("expand" to "container"))
                .authenticate(username, password)
                .responseObject(JsonDeserializer())

        val containingPageId = when (result) {
            is Result.Failure -> {
                throw RuntimeException("Well, that didn't work. \n ${result.error.response}")
            }
            is Result.Success -> {
                result.value.obj("container")?.string("id")?.toLong()!!
            }
        }

        return fetchPageObject(containingPageId)
    }

    fun searchForContent(cqlQuery: String, limit: Int = 25, size: Int = 25): List<String> {
        fun _searchForContent(previousContent: List<String>, start: Int = 0): List<String> {
            val (_, _, searchResults) = "$basePath/content/search"
                    .httpGet(listOf("expand" to "body.view", "limit" to limit, "size" to size, "start" to start, "cql" to cqlQuery))
                    .authenticate(username, password)
                    .responseObject(JsonDeserializer())

            when (searchResults) {
                is Result.Failure -> {
                    return emptyList()
                }
                is Result.Success -> {
                    val allContent = previousContent + searchResults.map { data ->
                        data.lookup<String>("results.body.view.value")
                    }.get().value

                    val hasNext = searchResults.get().obj("_links")?.contains("next") ?: false
                    if (hasNext) {
                        return allContent + _searchForContent(allContent, allContent.size)
                    } else {
                        return allContent
                    }
                }
            }
        }

        return _searchForContent(emptyList(), 0)
    }

    fun fetchPageComments(pageId: Long): List<String> {
        val (_, _, result) = "$basePath/content/$pageId/child/comment"
                .httpGet(listOf("expand" to "body.view", "depth" to "all"))
                .authenticate(username, password)
                .responseObject(JsonDeserializer())

        when (result) {
            is Result.Failure -> {
                throw RuntimeException("Well, that didn't work. \n ${result.error.response}")
            }
            is Result.Success -> {
                return result.map { data ->
                    data.lookup<String>("results.body.view.value")
                }.get().value
            }
        }
    }

    fun postCommentToPage(comment: String, parentPage: JsonObject): Unit {
        val commentObject = json {
            obj("type" to "comment",
                    "container" to parentPage,
                    "body" to json {
                        obj("storage" to json {
                            obj("value" to (comment + commentFooter),
                                    "representation" to "storage"
                            )
                        })
                    })
        }

        val (_, _, result) = "${basePath}/content"
                .httpPost()
                .header("Content-Type" to "application/json")
                .authenticate(username, password)
                .body(commentObject.toJsonString())
                .responseString()

        when (result) {
            is Result.Failure -> {
                throw RuntimeException("Well, that didn't work. \n ${result.error.response}")
            }
        }
    }


    fun replyToComment(commentText: String, commentId: Long, parentPage: JsonObject) {
        val commentObject = json {
            obj("type" to "comment",
                    "container" to parentPage,
                    "ancestors" to json {
                        array(obj(
                                "id" to commentId.toString(),
                                "type" to "comment"))
                    },
                    "body" to json {
                        obj(
                                "storage" to json {
                                    obj(
                                            "value" to (commentText + commentFooter),
                                            "representation" to "storage"
                                    )
                                })
                    })
        }

        val (_, _, result) = "$basePath/content"
                .httpPost()
                .header("Content-Type" to "application/json")
                .authenticate(username, password)
                .body(commentObject.toJsonString())
                .responseString()

        when (result) {
            is Result.Failure -> {
                throw RuntimeException("Well, that didn't work. \n ${result.error.response}")
            }
        }
    }

    private class JsonDeserializer : ResponseDeserializable<JsonObject> {

        val parser = Parser()

        override fun deserialize(inputStream: InputStream): JsonObject? {
            return parser.parse(inputStream, Charset.forName("UTF-8")) as JsonObject
        }
    }

}
