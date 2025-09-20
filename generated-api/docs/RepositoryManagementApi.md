# RepositoryManagementApi

All URIs are relative to *http://localhost:8080*

Method | HTTP request | Description
------------- | ------------- | -------------
[**createRepository**](RepositoryManagementApi.md#createRepository) | **POST** /api/v1/repositories | Create repository
[**getAllRepositories**](RepositoryManagementApi.md#getAllRepositories) | **GET** /api/v1/repositories | Get all repositories


<a id="createRepository"></a>
# **createRepository**
> Repository createRepository(repositoryCreateRequest)

Create repository

Creates a new repository

### Example
```kotlin
// Import classes:
//import net.kigawa.keruta.executor.client.infrastructure.*
//import net.kigawa.keruta.executor.client.model.*

val apiInstance = RepositoryManagementApi()
val repositoryCreateRequest : RepositoryCreateRequest =  // RepositoryCreateRequest | 
try {
    val result : Repository = apiInstance.createRepository(repositoryCreateRequest)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling RepositoryManagementApi#createRepository")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling RepositoryManagementApi#createRepository")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **repositoryCreateRequest** | [**RepositoryCreateRequest**](RepositoryCreateRequest.md)|  |

### Return type

[**Repository**](Repository.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a id="getAllRepositories"></a>
# **getAllRepositories**
> kotlin.collections.List&lt;Repository&gt; getAllRepositories()

Get all repositories

Retrieves all repositories

### Example
```kotlin
// Import classes:
//import net.kigawa.keruta.executor.client.infrastructure.*
//import net.kigawa.keruta.executor.client.model.*

val apiInstance = RepositoryManagementApi()
try {
    val result : kotlin.collections.List<Repository> = apiInstance.getAllRepositories()
    println(result)
} catch (e: ClientException) {
    println("4xx response calling RepositoryManagementApi#getAllRepositories")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling RepositoryManagementApi#getAllRepositories")
    e.printStackTrace()
}
```

### Parameters
This endpoint does not need any parameter.

### Return type

[**kotlin.collections.List&lt;Repository&gt;**](Repository.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

