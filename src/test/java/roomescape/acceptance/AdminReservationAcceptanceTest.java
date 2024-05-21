package roomescape.acceptance;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Cookie;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import roomescape.global.dto.ErrorResponse;
import roomescape.member.domain.Member;
import roomescape.reservation.dto.request.AdminReservationSaveRequest;
import roomescape.reservation.dto.response.ReservationResponse;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static roomescape.TestFixture.ADMIN_EMAIL;
import static roomescape.TestFixture.MIA_NAME;
import static roomescape.TestFixture.MIA_RESERVATION_DATE;

public class AdminReservationAcceptanceTest extends AcceptanceTest {

    @Test
    @DisplayName("관리자 권한으로 예약을 추가한다.")
    void createReservation() {
        // given
        Member admin = createTestAdmin();
        String token = createTestToken(admin.getEmail().getValue());
        Long themeId = createTestTheme();
        Long timeId = createTestReservationTime();
        Long memberID = createTestMember().getId();

        AdminReservationSaveRequest request = new AdminReservationSaveRequest(
                MIA_RESERVATION_DATE, timeId, themeId, memberID);
        Cookie cookie = new Cookie.Builder("token", token).build();

        // when
        ExtractableResponse<Response> response = RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .cookie(cookie)
                .body(request)
                .when().post("/admin/reservations")
                .then().log().all()
                .extract();
        ReservationResponse reservationResponse = response.as(ReservationResponse.class);

        // then
        assertSoftly(softly -> {
            checkHttpStatusCreated(softly, response);
            softly.assertThat(reservationResponse.id()).isNotNull();
            softly.assertThat(reservationResponse.memberName()).isEqualTo(MIA_NAME);
        });
    }

    @Test
    @DisplayName("사용자가 관리자 예약 추가 기능을 사용한다.")
    void createReservationWithoutAuthority() {
        // given
        Long themeId = createTestTheme();
        Long timeId = createTestReservationTime();
        Member member = createTestMember();
        String token = createTestToken(member.getEmail().getValue());

        AdminReservationSaveRequest request = new AdminReservationSaveRequest(
                MIA_RESERVATION_DATE, timeId, themeId, member.getId());
        Cookie cookie = new Cookie.Builder("token", token).build();

        // when
        ExtractableResponse<Response> response = RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .cookie(cookie)
                .body(request)
                .when().post("/admin/reservations")
                .then().log().all()
                .extract();
        ErrorResponse errorResponse = response.as(ErrorResponse.class);

        // then
        assertSoftly(softly -> {
            checkHttpStatusUnauthorized(softly, response);
            softly.assertThat(errorResponse.message()).isNotNull();
        });
    }

    @Test
    @DisplayName("예약 목록을 조회한다.")
    void findReservations() {
        // given & when
        Member admin = createTestAdmin();
        String token = createTestToken(admin.getEmail().getValue());
        Cookie cookie = new Cookie.Builder("token", token).build();

        ExtractableResponse<Response> response = RestAssured.given().log().all()
                .cookie(cookie)
                .when().get("/admin/reservations")
                .then().log().all()
                .extract();
        List<ReservationResponse> reservationResponses = Arrays.stream(response.as(ReservationResponse[].class))
                .toList();

        // then
        assertSoftly(softly -> {
            checkHttpStatusOk(softly, response);
            softly.assertThat(reservationResponses).hasSize(0);
        });
    }

    @TestFactory
    @DisplayName("예약을 추가하고 삭제한다.")
    Stream<DynamicTest> createThenDeleteReservation() {
        return Stream.of(
                dynamicTest("예약을 하나 생성한다.", this::createReservation),
                dynamicTest("예약이 하나 생성된 예약 목록을 조회한다.", () -> findReservationsWithSize(1)),
                dynamicTest("예약을 하나 삭제한다.", this::deleteOneReservation),
                dynamicTest("예약 목록을 조회한다.", () -> findReservationsWithSize(0))
        );
    }

    void deleteOneReservation() {
        // given & when
        String token = createTestToken(ADMIN_EMAIL);
        Cookie cookie = new Cookie.Builder("token", token).build();

        ExtractableResponse<Response> response = RestAssured.given().log().all()
                .cookie(cookie)
                .when().delete("/admin/reservations/1")
                .then().log().all()
                .extract();

        // then
        checkHttpStatusNoContent(response);
    }

    void findReservationsWithSize(int size) {
        // given & when
        String token = createTestToken(ADMIN_EMAIL);
        Cookie cookie = new Cookie.Builder("token", token).build();

        ExtractableResponse<Response> response = RestAssured.given().log().all()
                .cookie(cookie)
                .when().get("/admin/reservations")
                .then().log().all()
                .extract();
        List<ReservationResponse> reservationResponses = Arrays.stream(response.as(ReservationResponse[].class))
                .toList();

        // then
        assertSoftly(softly -> {
            checkHttpStatusOk(softly, response);
            softly.assertThat(reservationResponses).hasSize(size)
                    .extracting(ReservationResponse::id)
                    .isNotNull();
        });
    }
}
