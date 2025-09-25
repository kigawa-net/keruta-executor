# SessionApi

All URIs are relative to *http://localhost:8080*

Method | HTTP request | Description
------------- | ------------- | -------------
[**createSession**](SessionApi.md#createSession) | **POST** /api/v1/sessions | Create a new session
[**deleteSession**](SessionApi.md#deleteSession) | **DELETE** /api/v1/sessions/{sessionId} | Delete session
[**getAllSessions**](SessionApi.md#getAllSessions) | **GET** /api/v1/sessions | Get all sessions
[**getSessionById**](SessionApi.md#getSessionById) | **GET** /api/v1/sessions/{sessionId} | Get session by ID
[**updateSession**](SessionApi.md#updateSession) | **PUT** /api/v1/sessions/{sessionId} | Update session


<a id="createSession"></a>
# **createSession**
> Session createSession(sessionCreateRequest)

Create a new session

Creates a new session in the system

### Example
```kotlin
// Import classes:
//import net.kigawa.keruta.executor.client.infrastructure.*
//import net.kigawa.keruta.executor.client.model.*

val apiInstance = SessionApi()
val sessionCreateRequest : SessionCreateRequest =  // SessionCreateRequest | 
try {
    val result : Session = apiInstance.createSession(sessionCreateRequest)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling SessionApi#createSession")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling SessionApi#createSession")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **sessionCreateRequest** | [**SessionCreateRequest**](SessionCreateRequest.md)|  |

### Return type

[**Session**](Session.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a id="deleteSession"></a>
# **deleteSession**
> deleteSession(sessionId)

Delete session

Deletes a specific session

### Example
```kotlin
// Import classes:
//import net.kigawa.keruta.executor.client.infrastructure.*
//import net.kigawa.keruta.executor.client.model.*

val apiInstance = SessionApi()
val sessionId : kotlin.String = sessionId_example // kotlin.String | 
try {
    apiInstance.deleteSession(sessionId)
} catch (e: ClientException) {
    println("4xx response calling SessionApi#deleteSession")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling SessionApi#deleteSession")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **sessionId** | **kotlin.String**|  |

### Return type

null (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined

<a id="getAllSessions"></a>
# **getAllSessions**
> kotlin.collections.List&lt;Session&gt; getAllSessions()

Get all sessions

Retrieves all sessions in the system

### Example
```kotlin
// Import classes:
//import net.kigawa.keruta.executor.client.infrastructure.*
//import net.kigawa.keruta.executor.client.model.*

val apiInstance = SessionApi()
try {
    val result : kotlin.collections.List<Session> = apiInstance.getAllSessions()
    println(result)
} catch (e: ClientException) {
    println("4xx response calling SessionApi#getAllSessions")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling SessionApi#getAllSessions")
    e.printStackTrace()
}
```

### Parameters
This endpoint does not need any parameter.

### Return type

[**kotlin.collections.List&lt;Session&gt;**](Session.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="getSessionById"></a>
# **getSessionById**
> Session getSessionById(sessionId)

Get session by ID

Retrieves a specific session by its ID

### Example
```kotlin
// Import classes:
//import net.kigawa.keruta.executor.client.infrastructure.*
//import net.kigawa.keruta.executor.client.model.*

val apiInstance = SessionApi()
val sessionId : kotlin.String = sessionId_example // kotlin.String | 
try {
    val result : Session = apiInstance.getSessionById(sessionId)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling SessionApi#getSessionById")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling SessionApi#getSessionById")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **sessionId** | **kotlin.String**|  |

### Return type

[**Session**](Session.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="updateSession"></a>
# **updateSession**
> Session updateSession(sessionId, sessionUpdateRequest)

Update session

Updates an existing session

### Example
```kotlin
// Import classes:
//import net.kigawa.keruta.executor.client.infrastructure.*
//import net.kigawa.keruta.executor.client.model.*

val apiInstance = SessionApi()
val sessionId : kotlin.String = sessionId_example // kotlin.String | 
val sessionUpdateRequest : SessionUpdateRequest =  // SessionUpdateRequest | 
try {
    val result : Session = apiInstance.updateSession(sessionId, sessionUpdateRequest)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling SessionApi#updateSession")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling SessionApi#updateSession")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **sessionId** | **kotlin.String**|  |
 **sessionUpdateRequest** | [**SessionUpdateRequest**](SessionUpdateRequest.md)|  |

### Return type

[**Session**](Session.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

