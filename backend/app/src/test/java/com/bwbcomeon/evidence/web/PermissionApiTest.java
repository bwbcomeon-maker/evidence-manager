package com.bwbcomeon.evidence.web;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import com.bwbcomeon.evidence.service.AdminUserService;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * 权限 API 自动化测试：项目/成员/证据的可见性与越权拒绝。
 * 测试数据通过 API 创建（统一前缀 permtest_），结束后仅清理本次创建的数据。
 * 报告输出到 test-results/api（由 surefire 配置）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PermissionApiTest {

    @LocalServerPort
    private int port;

    private static final String PREFIX = "permtest_";
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PWD = "Init@12345";
    private static final String DEFAULT_PWD = "Init@12345";

    private static String adminSession;
    private static String pmoSession;
    private static String auditorSession;
    private static String userSession;
    private static Long pmoUserId;
    private static Long auditorUserId;
    private static Long userUserId;
    private static Long testProjectId;
    private static Long testEvidenceId;

    @BeforeAll
    static void initRestAssured() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @BeforeEach
    void setUpRestAssured() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
    }

    private RequestSpecification withSession(String sessionCookie) {
        if (sessionCookie == null) return given().contentType(ContentType.JSON);
        return given().contentType(ContentType.JSON).cookie("JSESSIONID", sessionCookie);
    }

    private String loginAndGetSession(String username, String password) {
        Response r = given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", username, "password", password))
                .when()
                .post("/api/auth/login");
        r.then().statusCode(200).body("code", equalTo(0));
        return r.getCookie("JSESSIONID");
    }

    /** 创建测试用户与项目（仅 ADMIN 可创建用户/项目） */
    @Test
    @Order(1)
    void setup_createTestUsersAndProject() {
        adminSession = loginAndGetSession(ADMIN_USER, ADMIN_PWD);
        Assertions.assertNotNull(adminSession);

        // 创建 PMO / AUDITOR / USER
        Response createPmo = withSession(adminSession)
                .body(Map.of(
                        "username", PREFIX + "pmo",
                        "password", DEFAULT_PWD,
                        "realName", "PMO测试",
                        "roleCode", "PMO",
                        "enabled", true))
                .when().post("/api/admin/users");
        createPmo.then().statusCode(200).body("code", equalTo(0));
        pmoUserId = createPmo.path("data.id");

        Response createAuditor = withSession(adminSession)
                .body(Map.of(
                        "username", PREFIX + "auditor",
                        "password", DEFAULT_PWD,
                        "realName", "审计测试",
                        "roleCode", "AUDITOR",
                        "enabled", true))
                .when().post("/api/admin/users");
        createAuditor.then().statusCode(200).body("code", equalTo(0));
        auditorUserId = createAuditor.path("data.id");

        Response createUser = withSession(adminSession)
                .body(Map.of(
                        "username", PREFIX + "user",
                        "password", DEFAULT_PWD,
                        "realName", "普通用户测试",
                        "roleCode", "USER",
                        "enabled", true))
                .when().post("/api/admin/users");
        createUser.then().statusCode(200).body("code", equalTo(0));
        userUserId = createUser.path("data.id");

        pmoSession = loginAndGetSession(PREFIX + "pmo", DEFAULT_PWD);
        auditorSession = loginAndGetSession(PREFIX + "auditor", DEFAULT_PWD);
        userSession = loginAndGetSession(PREFIX + "user", DEFAULT_PWD);

        // 创建项目（PMO 或 ADMIN）
        Response createProject = withSession(adminSession)
                .body(Map.of(
                        "code", "PERMTEST001",
                        "name", "权限测试项目_" + PREFIX,
                        "description", "E2E权限测试用"))
                .when().post("/api/projects");
        createProject.then().statusCode(200).body("code", equalTo(0));
        testProjectId = createProject.path("data.id");

        // 将 permtest_user 加入项目为 editor
        withSession(adminSession)
                .body(Map.of("userId", userUserId, "role", "editor"))
                .when().post("/api/projects/" + testProjectId + "/members")
                .then().statusCode(200).body("code", equalTo(0));

        // 获取 stageId + evidenceTypeCode 并上传一条证据
        Response progress = withSession(adminSession)
                .when().get("/api/projects/" + testProjectId + "/stage-progress");
        progress.then().statusCode(200).body("code", equalTo(0));
        List<Map<String, Object>> stages = progress.path("data.stages");
        if (stages != null && !stages.isEmpty()) {
            Object stageId = stages.get(0).get("stageId");
            List<Map<String, Object>> items = (List<Map<String, Object>>) stages.get(0).get("items");
            if (items != null && !items.isEmpty()) {
                String evidenceTypeCode = (String) items.get(0).get("evidenceTypeCode");
                File file = createTempTxt();
                Response upload = given().cookie("JSESSIONID", adminSession)
                        .multiPart("name", "权限测试证据")
                        .multiPart("stageId", String.valueOf(stageId))
                        .multiPart("evidenceTypeCode", evidenceTypeCode)
                        .multiPart("file", file)
                        .when().post("/api/projects/" + testProjectId + "/evidences");
                upload.then().statusCode(200).body("code", equalTo(0));
                testEvidenceId = upload.path("data.id");
            }
        }
    }

    private static File createTempTxt() {
        try {
            File f = File.createTempFile("permtest", ".txt");
            f.deleteOnExit();
            java.nio.file.Files.writeString(f.toPath(), "permtest content");
            return f;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @Order(2)
    void unauthenticated_returns401() {
        given().contentType(ContentType.JSON)
                .when().get("/api/projects")
                .then().statusCode(200).body("code", equalTo(401));
    }

    @Test
    @Order(3)
    void projectList_onlyVisibleProjects() {
        withSession(userSession)
                .when().get("/api/projects")
                .then().statusCode(200).body("code", equalTo(0))
                .body("data", notNullValue())
                .body("data.id", hasItem(testProjectId.intValue()));
    }

    @Test
    @Order(4)
    void projectDetail_visibleProject_ok() {
        withSession(userSession)
                .when().get("/api/projects/" + testProjectId)
                .then().statusCode(200).body("code", equalTo(0)).body("data.id", equalTo(testProjectId.intValue()));
    }

    @Test
    @Order(5)
    void projectCreate_userForbidden() {
        withSession(userSession)
                .body(Map.of("code", "USERPROJ", "name", "用户创建项目"))
                .when().post("/api/projects")
                .then().statusCode(200).body("code", equalTo(403));
    }

    @Test
    @Order(6)
    void projectCreate_pmoAllowed() {
        withSession(pmoSession)
                .body(Map.of("code", "PMOPROJ_" + System.currentTimeMillis(), "name", "PMO创建项目"))
                .when().post("/api/projects")
                .then().statusCode(200).body("code", equalTo(0));
    }

    @Test
    @Order(7)
    void adminUsers_userForbidden() {
        withSession(userSession)
                .when().get("/api/admin/users?page=1&pageSize=10")
                .then().statusCode(200).body("code", equalTo(403));
    }

    @Test
    @Order(8)
    void adminSelfOperation_disableSelfForbidden() {
        Long adminId = withSession(adminSession).when().get("/api/auth/me")
                .path("data.id");
        withSession(adminSession)
                .body(Map.of("enabled", false))
                .when().patch("/api/admin/users/" + adminId + "/enable")
                .then().statusCode(200).body("code", equalTo(AdminUserService.SELF_OPERATION_FORBIDDEN_CODE));
    }

    @Test
    @Order(9)
    void evidenceList_visibleProjectOnly() {
        withSession(userSession)
                .when().get("/api/projects/" + testProjectId + "/evidences")
                .then().statusCode(200).body("code", equalTo(0));
    }

    @Test
    @Order(10)
    void evidenceSubmit_draftAllowed() {
        if (testEvidenceId == null) return;
        withSession(adminSession)
                .when().post("/api/evidence/" + testEvidenceId + "/submit")
                .then().statusCode(200).body("code", equalTo(0));
    }

    @Test
    @Order(11)
    void cleanup_deleteTestData() {
        if (testProjectId != null && userUserId != null) {
            withSession(adminSession).when().delete("/api/projects/" + testProjectId + "/members/" + userUserId).then().statusCode(200);
        }
        if (pmoUserId != null) {
            withSession(adminSession).when().delete("/api/admin/users/" + pmoUserId).then().statusCode(200);
        }
        if (auditorUserId != null) {
            withSession(adminSession).when().delete("/api/admin/users/" + auditorUserId).then().statusCode(200);
        }
        if (userUserId != null) {
            withSession(adminSession).when().delete("/api/admin/users/" + userUserId).then().statusCode(200);
        }
    }
}
