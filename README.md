# stomp-webSocket-java-client
이 프로젝트는 java로 개발된 websocket방식으로 통신하는 STOMP 클라이언트 라이브러리 입니다.


현재 STOMP 홈페이지(https://stomp.github.io) 에 보면 스팩 구현채가 여러개 나와 있지만 여러가지로 테스트를 해본 결과
자바로 구현된 클라이언트가 webSocket과 연동할수 없거나 아직 버전이 1.0 이 되지 않은 미완성 상태인등 사용상 문제가 많았습니다.

그래서 직접 STOMP client 구현을 하게 되었습니다.


# 개발 목표
android app에서 사용가능한 STOMP over webSocket client 구현체를 만드는 것이 목표입니다.





# 개발 상태
이미개발되어 있는 stomp.js (http://jmesnil.net/stomp-websocket/doc) 구현체를 보고 거의 비슷한 형태로 개발하였습니다. 현재까지는 거의 모든 기능에 대해서 개발만
된 상태입니다. 단위 테스트와 서버와 실제 통신하는 테스트까지 진행한 이후에 0.1 버전을 만들 계획입니다.






