package org.kaldi.demo.service;

import org.kaldi.demo.jsonmodel.JsonModel;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;

public interface LocalAPI {

    @GET("v1/person")
    Call<List<JsonModel>> getdata();

    }


