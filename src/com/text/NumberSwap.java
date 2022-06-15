package com.text;

public class NumberSwap {
	

	public static void main(String args[]) {
		int i=15;
		int j=20;
		
		System.out.println("i initial value" + i);
		System.out.println("j initial value" + j);
		
	i=j+i;//35
	j=i-j;
	i=i-j;
	
	
	
	System.out.println("i value" + i);
	System.out.println("j value" + j);
	}
	

}
