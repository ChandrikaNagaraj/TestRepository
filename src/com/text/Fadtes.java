package com.text;

public class Fadtes {

	public static void main(String[] args) {
		
		 String expMonth ="07";
		 String expYear  ="127";   
		 String cccode   ="AMEX";  
		 String ccanme   ="PSD2";   
		 String creditcardnum="378004196381319";
		 java.sql.Date ccexp    = new java.sql.Date(
							      Integer.parseInt(expYear),
							      Integer.parseInt(expMonth),28);
		 
		 System.out.println(ccexp);
		}

	

}
