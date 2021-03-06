package com.github.jrcodeza.schema.v2.generator.domain;

import com.github.jrcodeza.OpenApiIgnore;

public class Customer extends Entity {

	private boolean vip;

	private Product topCustomerProduct;

	@OpenApiIgnore
	private String toBeIgnored;

	public boolean isVip() {
		return vip;
	}

	public void setVip(boolean vip) {
		this.vip = vip;
	}

	public Product getTopCustomerProduct() {
		return topCustomerProduct;
	}

	public void setTopCustomerProduct(Product topCustomerProduct) {
		this.topCustomerProduct = topCustomerProduct;
	}

	public String getToBeIgnored() {
		return toBeIgnored;
	}

	public void setToBeIgnored(String toBeIgnored) {
		this.toBeIgnored = toBeIgnored;
	}
}
