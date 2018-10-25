package com.articulate.nlp.imsclient;

import java.util.List;

public class ParseResult {	

	private String word;
	private List<List<TotalSense>> totals;
	
	public ParseResult() {}

	public ParseResult(String word, List<List<TotalSense>> totals) {
		super();
		this.word = word;
		this.totals = totals;
	}

	public String getWord() {
		return word;
	}
	
	public void setWord(String word) {
		this.word = word;
	}	
	
	public List<List<TotalSense>> getTotals() {
		return totals;
	}

	public void setTotals(List<List<TotalSense>> totals) {
		this.totals = totals;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((totals == null) ? 0 : totals.hashCode());
		result = prime * result + ((word == null) ? 0 : word.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ParseResult other = (ParseResult) obj;
		if (totals == null) {
			if (other.totals != null)
				return false;
		} else if (!totals.equals(other.totals))
			return false;
		if (word == null) {
			if (other.word != null)
				return false;
		} else if (!word.equals(other.word))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "ParseResult [word=" + word + ", totals=" + totals + "]";
	}

}
