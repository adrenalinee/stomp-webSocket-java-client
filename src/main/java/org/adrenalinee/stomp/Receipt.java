package org.adrenalinee.stomp;

import org.adrenalinee.stomp.listener.ReceiptListener;

/**
 * 
 * @author 신동성
 * @since 2015. 12. 3.
 */
public class Receipt {
	private String receiptId;
	
	private ReceiptListener receiptListener;

	public String getReceiptId() {
		return receiptId;
	}

	public void setReceiptId(String receiptId) {
		this.receiptId = receiptId;
	}

	public ReceiptListener getReceiptListener() {
		return receiptListener;
	}

	public void setReceiptListener(ReceiptListener receiptListener) {
		this.receiptListener = receiptListener;
	}
	
}
