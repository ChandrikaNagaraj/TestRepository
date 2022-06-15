package com.text;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class NewJavaFeatures {

	public static void main(String arg[]) {
		List<Integer> alist = Arrays.asList(10, 18, 25, 30, 14, 16);

		

		alist.forEach(n -> { if(n%5==0) System.out.println(n); } );

	}

}
