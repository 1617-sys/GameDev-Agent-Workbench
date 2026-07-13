package com.example.gameworkbench.service;
import java.util.*; import org.springframework.stereotype.Service;
@Service public class FakeEmbeddingProvider implements EmbeddingProvider { public String model(){return "fake-hash-v1";} public int dimension(){return 8;} public List<float[]> embed(List<String> in){return in.stream().map(s->{float[] v=new float[8]; for(int i=0;i<s.length();i++)v[i%8]+=(s.charAt(i)%31); return v;}).toList();} }
