package de.tinloaf.cloudy.text;

import org.lemurproject.kstem.KrovetzStemmer;

public class PreKrovetzStemmer extends Stemmer{
	
	
	
	
	@Override
	public String stem(String str) {
		KrovetzStemmer a =new KrovetzStemmer();
		return a.stem(str);
	}

}
