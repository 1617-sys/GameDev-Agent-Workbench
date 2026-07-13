package com.example.gameworkbench.service;
import java.util.*; import org.springframework.stereotype.Service;
@Service public class KnowledgeChunker { public static final String VERSION="v1-400-40"; public List<String> chunk(String text){ List<String> out=new ArrayList<>(); String s=text.trim(); for(int p=0;p<s.length();){int e=Math.min(s.length(),p+400); out.add(s.substring(p,e)); if(e==s.length())break; p=e-40;} return out;} }
