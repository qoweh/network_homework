package com.demo;

import java.util.ArrayList;

/**
 * BaseLayer - 계층 간 통신을 위한 기본 인터페이스
 * 
 * 네트워크 프로토콜 스택의 각 계층(Physical, Ethernet, ChatApp)이 구현하는 공통 인터페이스입니다.
 * OSI 7계층 모델의 계층화 개념을 단순화하여 구현했습니다.
 * 
 * 주요 개념:
 * - UnderLayer: 하위 계층 (예: Ethernet의 하위는 Physical)
 * - UpperLayer: 상위 계층 (예: Ethernet의 상위는 ChatApp)
 * - Send: 데이터를 하위 계층으로 전달 (송신 방향)
 * - Receive: 데이터를 상위 계층에서 수신 (수신 방향)
 * 
 * 데이터 흐름 예시:
 *   송신: ChatApp --Send--> Ethernet --Send--> Physical --> 네트워크
 *   수신: 네트워크 --> Physical --Receive--> Ethernet --Receive--> ChatApp
 */
public interface BaseLayer {
	// 인터페이스의 필드는 자동으로 public static final이므로 실제로는 사용되지 않음
	// 각 구현 클래스에서 자체적으로 필드를 선언합니다
	public final String layerName = null;
	public final BaseLayer underLayer = null;
	public final ArrayList<BaseLayer> upperLayers = new ArrayList<BaseLayer>();
	public final int upperLayerCount = 0;

	/**
	 * 계층의 이름을 반환합니다.
	 * @return 계층 이름 (예: "Physical", "Ethernet", "ChatApp")
	 */
	public String GetLayerName();

	/**
	 * 하위 계층을 반환합니다.
	 * @return 하위 계층 객체 (예: Ethernet 계층의 경우 Physical 반환)
	 */
	public BaseLayer GetUnderLayer();

	/**
	 * 상위 계층을 인덱스로 반환합니다.
	 * 하나의 계층이 여러 상위 계층을 가질 수 있으므로 리스트로 관리합니다.
	 * @param index 상위 계층 인덱스 (0부터 시작)
	 * @return 상위 계층 객체
	 */
	public BaseLayer GetUpperLayer(int index);

	/**
	 * 하위 계층을 설정합니다.
	 * @param pUnderLayer 연결할 하위 계층
	 */
	public void SetUnderLayer(BaseLayer pUnderLayer);

	/**
	 * 상위 계층을 추가합니다.
	 * @param pUpperLayer 연결할 상위 계층
	 */
	public void SetUpperLayer(BaseLayer pUpperLayer);

	/**
	 * 데이터를 하위 계층으로 전송합니다. (송신 방향)
	 * 기본 구현은 false를 반환하며, 각 계층에서 오버라이드하여 실제 동작을 구현합니다.
	 * 
	 * 호출 순서 예시:
	 *   ChatApp.Send() → Ethernet.Send() → Physical.Send() → 네트워크 카드
	 * 
	 * @param input 전송할 데이터 바이트 배열
	 * @param length 전송할 데이터 길이
	 * @return 전송 성공 여부
	 */
	public default boolean Send(byte[] input, int length) {
		return false;
	}
	
	/**
	 * 파일명을 받아 전송하는 메서드 (확장용, 현재 미사용)
	 * @param filename 전송할 파일명
	 * @return 전송 성공 여부
	 */
	public default boolean Send(String filename) {
		return false;
	}

	/**
	 * 하위 계층으로부터 데이터를 수신하여 상위 계층으로 전달합니다. (수신 방향)
	 * 각 계층에서 오버라이드하여 프로토콜별 처리(헤더 제거, 필터링 등)를 수행합니다.
	 * 
	 * 호출 순서 예시:
	 *   네트워크 카드 → Physical.Receive() → Ethernet.Receive() → ChatApp.Receive()
	 * 
	 * @param input 수신한 데이터 바이트 배열
	 * @return 처리 성공 여부
	 */
	public default boolean Receive(byte[] input) {
		return false;
	}

	/**
	 * 매개변수 없이 수신을 처리하는 메서드 (확장용, 현재 미사용)
	 * @return 처리 성공 여부
	 */
	public default boolean Receive() {
		return false;
	}
}
