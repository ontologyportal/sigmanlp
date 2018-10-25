package com.articulate.nlp.imsclient;

public class TotalSense {

	private String sense;
	private double probability;
	
	public TotalSense() {}

	public TotalSense(String sense, double probability) {
		super();
		this.sense = sense;
		this.probability = probability;
	}

	public String getSense() {
		return sense;
	}
	public void setSense(String sense) {
		this.sense = sense;
	}
	public double getProbability() {
		return probability;
	}
	public void setProbability(double probability) {
		this.probability = probability;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		long temp;
		temp = Double.doubleToLongBits(probability);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		result = prime * result + ((sense == null) ? 0 : sense.hashCode());
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
		TotalSense other = (TotalSense) obj;
		if (Double.doubleToLongBits(probability) != Double.doubleToLongBits(other.probability))
			return false;
		if (sense == null) {
			if (other.sense != null)
				return false;
		} else if (!sense.equals(other.sense))
			return false;
		return true;
	}
	@Override
	public String toString() {
		return "TotalSense [sense=" + sense + ", probability=" + probability + "]";
	}
	
	
}
