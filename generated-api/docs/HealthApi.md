# HealthApi

All URIs are relative to *http://localhost:8080*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getHealth**](HealthApi.md#getHealth) | **GET** /api/v1/health | Health check


<a id="getHealth"></a>
# **getHealth**
> GetHealth200Response getHealth()

Health check

Returns the health status of the application

### Example
```kotlin
// Import classes:
//import net.kigawa.keruta.executor.client.infrastructure.*
//import net.kigawa.keruta.executor.client.model.*

val apiInstance = HealthApi()
try {
    val result : GetHealth200Response = apiInstance.getHealth()
    println(result)
} catch (e: ClientException) {
    println("4xx response calling HealthApi#getHealth")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling HealthApi#getHealth")
    e.printStackTrace()
}
```

### Parameters
This endpoint does not need any parameter.

### Return type

[**GetHealth200Response**](GetHealth200Response.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

