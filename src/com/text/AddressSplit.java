package com.text;

public class AddressSplit {

	public static void main(String[] args) {
		String fstr="Warehouse and Finance<BR>Saxon Way<BR>Bar Hill<BR>.<BR>CB23 9FG<BR>UNITED KINGDOM";
		//int getCount=fstr.indexOf("<BR>");
		
		int nextCount=fstr.indexOf("<BR>", 0);
		int aftercount=nextCount+1;
		
		
		while (nextCount != -1) {
		 String nstr=	fstr.substring(nextCount, aftercount);
		 System.out.println(nstr);
			
			
			
		}
		
		
		
		
		
		

	}

}
