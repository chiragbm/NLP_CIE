package cie_package;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class EvaluateResults {
	
	static String re_filename = "nyt_extractions.txt";
	static String my_filename = "nyt_out_sentences.txt";
	
	public static void main(String a[]) throws IOException{
		
		FileReader re_reader = new FileReader(re_filename);
		FileReader my_reader = new FileReader(my_filename);
		
		int re_count = 0;
		int my_count = 0;
		
		BufferedReader rbf = new BufferedReader(re_reader);
		BufferedReader mbf = new BufferedReader(my_reader);
		String line = null;
		int i = 0;
		while((line = rbf.readLine())!=null){
			if(i%2 !=0){
				re_count+=Integer.parseInt(line);
			}
			i++;
		}
		
		System.out.println(re_count);
		i = 0;
		while((line = mbf.readLine())!=null){
			if(i%2 !=0){
				my_count+=Integer.parseInt(line);
			}
			i++;
		}
		
		System.out.println(my_count);
		
		float per = ((my_count) * 100.00f) / (re_count);
		
		System.out.println(per);
		
	}
	
	
	
}
