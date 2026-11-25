package org.courseflow.integration.regression;

import static org.courseflow.integration.support.JsonChecks.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.courseflow.integration.support.HttpIntegrationTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/** Ordered port of the verified 6.23 HTTP regression. Each step reports separately. */
@Execution(ExecutionMode.SAME_THREAD)
class CourseFlowRegressionIT extends HttpIntegrationTest {

  private boolean failed;
  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.jdbc.core.JdbcTemplate jdbc;

  private long dbCount(String sql, Object... args) {
    return java.util.Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args));
  }

  private long scenarioId(String name) {
    return Long.parseLong(get(name));
  }

  private void checkError(Response response, int status, String code) {
    org.junit.jupiter.api.Assertions.assertEquals(status, response.status());
    org.junit.jupiter.api.Assertions.assertEquals((long) status, requiredInteger(response.body(), "status"));
    org.junit.jupiter.api.Assertions.assertEquals(code, requiredField(response.body(), "code"));
  }

  @TestFactory
  Stream<DynamicTest> fullRestRegression() {
    return Stream.of(
        scenario("001. Login admin — 200", this::case001),
        scenario("002. System status (ADMIN) — 200", this::case002),
        scenario("003. Anonymous actuator health — status only", this::case003),
        scenario("004. Allow configured React CORS preflight — 200", this::case004),
        scenario("005. Reject untrusted CORS origin — 403", this::case005),
        scenario("006. Register student — 201", this::case006),
        scenario("007. Duplicate registration — 409", this::case007),
        scenario("008. Wrong password — 401", this::case008),
        scenario("009. Login student — 200", this::case009),
        scenario("010. Own user profile — 200", this::case010),
        scenario("011. Guest denied — 401", this::case011),
        scenario("012. Invalid token denied — 401", this::case012),
        scenario("013. Student cannot list users — 403", this::case013),
        scenario("014. Student cannot read another user — 403", this::case014),
        scenario("015. Create teacher — 201", this::case015),
        scenario("016. Login teacher — 200", this::case016),
        scenario("017. Create temporary account — 201", this::case017),
        scenario("018. Login other — 200", this::case018),
        scenario("019. Admin reads user — 200", this::case019),
        scenario("020. Admin filters and sorts users — 200", this::case020),
        scenario("021. Update temporary user — 200", this::case021),
        scenario("022. Email change revokes previous token — 401", this::case022),
        scenario("023. Login other — 200", this::case023),
        scenario("024. Assign temporary teacher role — 200", this::case024),
        scenario("025. Role change revokes previous token — 401", this::case025),
        scenario("026. Login other — 200", this::case026),
        scenario("027. Reject empty roles — 400", this::case027),
        scenario("028. System admin cannot be deactivated — 409", this::case028),
        scenario("029. Change student password — 204", this::case029),
        scenario("030. Password change revokes session — 401", this::case030),
        scenario("031. Old password rejected — 401", this::case031),
        scenario("032. Login student — 200", this::case032),
        scenario("033. Logout student — 204", this::case033),
        scenario("034. Logged out token rejected — 401", this::case034),
        scenario("035. Login student — 200", this::case035),
        scenario("036. Forgot password unknown email — 202", this::case036),
        scenario("037. Reject invalid reset token — 400", this::case037),
        scenario("038. Admin reset nonexistent account — 404", this::case038),
        scenario("039. Create category — 201", this::case039),
        scenario("040. Duplicate category — 409", this::case040),
        scenario("041. Student cannot create category — 403", this::case041),
        scenario("042. Reject blank category name — 400", this::case042),
        scenario("043. Public category list and search — 200", this::case043),
        scenario("044. Public category detail — 200", this::case044),
        scenario("045. Invalid page size — 400", this::case045),
        scenario("046. Missing category — 404", this::case046),
        scenario("047. Create tag — 201", this::case047),
        scenario("048. Update tag — 200", this::case048),
        scenario("049. Public tag detail — 200", this::case049),
        scenario("050. Public tags — 200", this::case050),
        scenario("051. Create main course — 201", this::case051),
        scenario("052. Update main course — 200", this::case052),
        scenario("053. Public course detail — 200", this::case053),
        scenario("054. Course search treats percent literally — 200", this::case054),
        scenario("055. Public filtered course list — 200", this::case055),
        scenario("056. Other teacher cannot edit course — 403", this::case056),
        scenario("057. Create temporary course — 201", this::case057),
        scenario("058. Admin reassigns temporary course — 200", this::case058),
        scenario("059. Archive temporary course — 200", this::case059),
        scenario("060. Cannot enroll in archived course — 409", this::case060),
        scenario("061. Delete empty archived course — 204", this::case061),
        scenario("062. Deleted course not found — 404", this::case062),
        scenario("063. Attach tag to course — 204", this::case063),
        scenario("064. List attached tags — 200", this::case064),
        scenario("065. Cannot delete attached tag — 409", this::case065),
        scenario("066. Detach tag — 204", this::case066),
        scenario("067. Delete unused tag — 204", this::case067),
        scenario("068. Create module — 201", this::case068),
        scenario("069. Reject duplicate module position — 409", this::case069),
        scenario("070. Create temporary module — 201", this::case070),
        scenario("071. Update module — 200", this::case071),
        scenario("072. Reorder module — 200", this::case072),
        scenario("073. Read module — 200", this::case073),
        scenario("074. List modules — 200", this::case074),
        scenario("075. Create lesson — 201", this::case075),
        scenario("076. Create temporary lesson — 201", this::case076),
        scenario("077. Update lesson — 200", this::case077),
        scenario("078. Reject unsafe video URL — 400", this::case078),
        scenario("079. Reject oversized lesson content — 400", this::case079),
        scenario("080. Reorder lesson — 200", this::case080),
        scenario("081. Delete empty lesson — 204", this::case081),
        scenario("082. Read lesson — 200", this::case082),
        scenario("083. List lessons — 200", this::case083),
        scenario("084. Unenrolled student cannot read lessons — 403", this::case084),
        scenario("085. Create session — 201", this::case085),
        scenario("086. Reject overlapping session — 409", this::case086),
        scenario("087. Update session — 200", this::case087),
        scenario("088. Reschedule session — 200", this::case088),
        scenario("089. Session detail — 200", this::case089),
        scenario("090. List sessions — 200", this::case090),
        scenario("091. ONLINE session requires a link — 400", this::case091),
        scenario("092. Reject unsafe meeting link — 400", this::case092),
        scenario("093. OFFLINE session must not have link — 400", this::case093),
        scenario("094. Create temporary offline session — 201", this::case094),
        scenario("095. Delete temporary session — 204", this::case095),
        scenario("096. Enroll student — 201", this::case096),
        scenario("097. Repeat enrollment idempotently — 200", this::case097),
        scenario("098. Teacher cannot enroll in own course — 409", this::case098),
        scenario("099. My enrollment — 200", this::case099),
        scenario("100. Course roster — 200", this::case100),
        scenario("101. My enrollments — 200", this::case101),
        scenario("102. Student reads enrolled modules — 200", this::case102),
        scenario("103. Student reads enrolled lessons — 200", this::case103),
        scenario("104. Student reads schedule — 200", this::case104),
        scenario("105. Student cannot read roster — 403", this::case105),
        scenario("106. Other teacher cannot read roster — 403", this::case106),
        scenario("107. Create assignment — 201", this::case107),
        scenario("108. Update assignment — 200", this::case108),
        scenario("109. Create second assignment — 201", this::case109),
        scenario("110. Create temporary assignment — 201", this::case110),
        scenario("111. Delete unused assignment — 204", this::case111),
        scenario("112. Read assignment — 200", this::case112),
        scenario("113. List assignments — 200", this::case113),
        scenario("114. Unenrolled user cannot submit — 403", this::case114),
        scenario("115. Submit homework — 201", this::case115),
        scenario("116. Reject duplicate homework — 409", this::case116),
        scenario("117. Read own submission — 200", this::case117),
        scenario("118. Teacher lists pending work — 200", this::case118),
        scenario("119. Student cannot list submissions — 403", this::case119),
        scenario("120. Other teacher cannot list submissions — 403", this::case120),
        scenario("121. Reject score above maximum — 400", this::case121),
        scenario("122. Teacher dashboard pending queue — 200", this::case122),
        scenario("123. Grade homework — 200", this::case123),
        scenario("124. Cannot edit submitted assignment — 409", this::case124),
        scenario("125. Cannot delete submitted assignment — 409", this::case125),
        scenario("126. Create quiz — 201", this::case126),
        scenario("127. Read quiz by module — 200", this::case127),
        scenario("128. Update quiz settings — 200", this::case128),
        scenario("129. Read quiz — 200", this::case129),
        scenario("130. Reject second quiz in same module — 409", this::case130),
        scenario("131. Create temporary quiz — 201", this::case131),
        scenario("132. Cannot start empty quiz — 409", this::case132),
        scenario("133. Create single choice question — 201", this::case133),
        scenario("134. Create multiple choice question — 201", this::case134),
        scenario("135. Reject duplicate question position — 409", this::case135),
        scenario("136. Create temporary question — 201", this::case136),
        scenario("137. Update temporary question — 200", this::case137),
        scenario("138. Delete temporary question — 204", this::case138),
        scenario("139. Create question in temporary quiz — 201", this::case139),
        scenario("140. Delete unused quiz with questions — 204", this::case140),
        scenario("141. Delete empty temporary module — 204", this::case141),
        scenario("142. Reject multiple choice without distractor — 400", this::case142),
        scenario("143. Teacher reads questions with answers — 200", this::case143),
        scenario("144. Questions unavailable before active attempt — 409", this::case144),
        scenario("145. Student cannot read answer keys — 403", this::case145),
        scenario("146. Start attempt 1 — 201", this::case146),
        scenario("147. Student reads questions without correct flags — 200", this::case147),
        scenario("148. Progress identifies active quiz — 200", this::case148),
        scenario("149. Gradebook identifies active quiz — 200", this::case149),
        scenario("150. Reject oversized quiz answer list — 400", this::case150),
        scenario("151. Resume same attempt — 200", this::case151),
        scenario("152. Read ongoing attempt — 200", this::case152),
        scenario("153. Freeze quiz after attempt starts — 409", this::case153),
        scenario("154. Reject answer from another question — 400", this::case154),
        scenario("155. Submit perfect attempt — 200", this::case155),
        scenario("156. Reject duplicate attempt submission — 409", this::case156),
        scenario("157. Start second attempt — 201", this::case157),
        scenario("158. Submit partial result — 200", this::case158),
        scenario("159. Reject attempt above limit — 409", this::case159),
        scenario("160. Student attempt history — 200", this::case160),
        scenario("161. Teacher quiz results — 200", this::case161),
        scenario("162. Own attempt detail — 200", this::case162),
        scenario("163. Management attempt detail — 200", this::case163),
        scenario("164. Student cannot access management result — 403", this::case164),
        scenario("165. Other student cannot read attempt — 404", this::case165),
        scenario("166. Questions unavailable after final attempt — 409", this::case166),
        scenario("167. Cannot delete quiz with attempts — 409", this::case167),
        scenario("168. Create review — 201", this::case168),
        scenario("169. Reject duplicate review — 409", this::case169),
        scenario("170. Update own review — 200", this::case170),
        scenario("171. Read own review — 200", this::case171),
        scenario("172. Public course reviews — 200", this::case172),
        scenario("173. Public aggregate rating — 200", this::case173),
        scenario("174. Delete own review — 204", this::case174),
        scenario("175. Empty aggregate rating — 200", this::case175),
        scenario("176. Restore review — 201", this::case176),
        scenario("177. Gradebook with both assessment types — 200", this::case177),
        scenario("178. Admin filtered gradebook — 200", this::case178),
        scenario("179. Student cannot read gradebook — 403", this::case179),
        scenario("180. Other teacher cannot read gradebook — 403", this::case180),
        scenario("181. Student progress — 200", this::case181),
        scenario("182. Student dashboard — 200", this::case182),
        scenario("183. Teacher dashboard graded work removed — 200", this::case183),
        scenario("184. Admin dashboard — 200", this::case184),
        scenario("185. Student cannot access admin dashboard — 403", this::case185),
        scenario("186. Student cannot access teacher dashboard — 403", this::case186),
        scenario("187. Admin without student role denied — 403", this::case187),
        scenario("188. Withdraw from course — 204", this::case188),
        scenario("189. Withdrawn student denied content — 403", this::case189),
        scenario("190. Submission history retained — 200", this::case190),
        scenario("191. Progress history retained — 200", this::case191),
        scenario("192. Withdrawn student in gradebook — 200", this::case192),
        scenario("193. Dashboard excludes withdrawn course — 200", this::case193),
        scenario("194. Admin enrollment count updated — 200", this::case194),
        scenario("195. Restore enrollment — 200", this::case195),
        scenario("196. Restored student access — 200", this::case196),
        scenario("197. Restored admin count — 200", this::case197),
        scenario("198. Cannot remove TEACHER with assigned course — 409", this::case198),
        scenario("199. Cannot remove STUDENT with active enrollment — 409", this::case199),
        scenario("200. Teacher cannot submit homework in own course — 409", this::case200),
        scenario("201. Other teacher enrolls as student — 201", this::case201),
        scenario("202. Cannot reassign course to enrolled student — 409", this::case202),
        scenario("203. Cannot update course with enrolled student as teacher — 409", this::case203),
        scenario("204. Teacher cannot administratively withdraw student — 403", this::case204),
        scenario("205. Student cannot administratively withdraw student — 403", this::case205),
        scenario("206. Cannot delete course with history — 409", this::case206),
        scenario("207. Cannot delete active account — 409", this::case207),
        scenario("208. Deactivate temporary account — 200", this::case208),
        scenario("209. Deactivation revokes JWT — 401", this::case209),
        scenario("210. Login other — 401", this::case210),
        scenario("211. Cannot delete inactive user with active enrollment — 409", this::case211),
        scenario("212. Admin withdraws inactive student — 204", this::case212),
        scenario("213. Admin withdrawal is idempotent — 204", this::case213),
        scenario("214. Admin sees withdrawn enrollment — 200", this::case214),
        scenario("215. Reactivate temporary account — 200", this::case215),
        scenario("216. Login other — 200", this::case216),
        scenario("217. Deactivate before deletion — 200", this::case217),
        scenario("218. Delete inactive account — 204", this::case218),
        scenario("219. Deleted user preserved as archive — 200", this::case219),
        scenario("220. Login other — 401", this::case220),
        scenario("221. Deleted account cannot be activated — 409", this::case221),
        scenario("222. Final student progress — 200", this::case222),
        scenario("223. Unknown login email — 401", this::case223),
        scenario("224. Oversized login password — 400", this::case224),
        scenario("225. Oversized UTF-8 login password — 400", this::case225),
        scenario("226. Oversized current password — 400", this::case226),
        scenario("227. Unknown API denied — 403", this::case227),
        scenario("228. HEAD administrative dashboard denied for student — 403", this::case228),
        scenario("229. Create mixed-case tag — 201", this::case229),
        scenario("230. Reject case-insensitive duplicate tag — 409", this::case230),
        scenario("231. Delete invariant test tag — 204", this::case231),
        scenario("232. Update category — 200", this::case232),
        scenario("233. Student cannot update category — 403", this::case233),
        scenario("234. Cannot delete category referenced by course — 409", this::case234),
        scenario("235. Create temporary category — 201", this::case235),
        scenario("236. Cannot rename category to existing name — 409", this::case236),
        scenario("237. Delete unused category — 204", this::case237),
        scenario("238. Deleted category not found — 404", this::case238),
        scenario("239. Read empty own profile — 200", this::case239),
        scenario("240. Create own profile — 200", this::case240),
        scenario("241. Read persisted profile — 200", this::case241),
        scenario("242. Reject unsafe avatar URL — 400", this::case242),
        scenario("243. Teacher has separate profile — 200", this::case243),
        scenario("244. Anonymous profile denied — 401", this::case244),
        scenario("245. Public teacher identity — 200", this::case245),
        scenario("246. Non-teacher not published — 404", this::case246),
        scenario("247. My current assignments — 200", this::case247),
        scenario("248. My pending assignments filter — 200", this::case248),
        scenario("249. My graded submissions — 200", this::case249),
        scenario("250. My quiz attempt history — 200", this::case250),
        scenario("251. Invalid page size rejected — 400", this::case251),
        scenario("252. Admin without student role denied history — 403", this::case252),
        scenario("253. Other student cannot see submissions — 200", this::case253),
        scenario("254. Other student cannot see quiz attempts — 200", this::case254),
        scenario("255. Withdraw to verify global history — 204", this::case255),
        scenario("256. Current assignments hidden after withdrawal — 200", this::case256),
        scenario("257. Submission history survives withdrawal — 200", this::case257),
        scenario("258. Quiz history survives withdrawal — 200", this::case258),
        scenario("259. Restore enrollment after history checks — 200", this::case259),
        scenario("260. Create course for archived dashboard check — 201", this::case260),
        scenario("261. Create second course module for schedule test — 201", this::case261),
        scenario("262. Create second course lesson for schedule test — 201", this::case262),
        scenario("263. Reject teacher schedule overlap across courses — 409", this::case263),
        scenario("264. Delete temporary schedule lesson — 204", this::case264),
        scenario("265. Delete temporary schedule module — 204", this::case265),
        scenario("266. Enroll in course before archive — 201", this::case266),
        scenario("267. Archive course with active enrollment — 200", this::case267),
        scenario("268. Dashboard excludes archived active enrollment — 200", this::case268),
        scenario("269. Withdraw from archived course — 204", this::case269),
        scenario("270. OpenAPI describes every REST operation — 200", this::case270),
        scenario("271. Swagger UI static page — 200", this::case271),
        scenario("272. Swagger configuration — 200", this::case272),
        scenario("273. Canonical category search parameter q — 200", this::case273),
        scenario("274. Reject unsupported login content type — 415", this::case274),
        scenario("275. Reject malformed JSON — 400", this::case275),
        scenario("276. Admin creates account with temporary password — 201", this::case276),
        scenario("277. Login requires password change — 200", this::case277),
        scenario("278. Restricted JWT cannot access own user endpoint — 403", this::case278),
        scenario("279. Restricted JWT cannot use STUDENT dashboard — 403", this::case279),
        scenario("280. Temporary password cannot be reused — 400", this::case280),
        scenario("281. Change temporary password — 204", this::case281),
        scenario("282. Restricted JWT revoked after change — 401", this::case282),
        scenario("283. Login with new password — 200", this::case283),
        scenario("284. Full access restored — 200", this::case284),
        scenario("285. Get current version of main course — 200", this::case285),
        scenario("286. Update using current version — 200", this::case286),
        scenario("287. Reject stale version — 409", this::case287),
        scenario("288. Reject missing version — 400", this::case288),
        scenario("289. Verify stale update did not overwrite course — 200", this::case289),
        scenario("290. Category case-insensitive duplicate — 409", this::case290),
        scenario("291. Category stale update — 409", this::case291),
        scenario("292. Category missing version — 400", this::case292),
        scenario("293. Category remains unchanged — 200", this::case293),
        scenario("294. Create version test tag — 201", this::case294),
        scenario("295. Tag update with valid version — 200", this::case295),
        scenario("296. Stale tag update — 409", this::case296),
        scenario("297. Tag update missing version — 400", this::case297),
        scenario("298. Tag name remains unchanged — 200", this::case298),
        scenario("299. Delete version test tag — 204", this::case299),
        scenario("300. Read quiz history sorted by effective status — 200", this::case300),
        scenario("301. Read quiz history sorted by effective finishedAt — 200", this::case301),
        scenario("302. Cannot deactivate teacher with active courses — 409", this::case302),
        scenario("303. Teacher remains active — 200", this::case303),
        scenario("304. Cannot archive course with future session — 409", this::case304),
        scenario("305. Course remains active — 200", this::case305),
        scenario("306. Cannot change course teacher through ordinary update — 409", this::case306),
        scenario("307. Cannot create session in past — 400", this::case307),
        scenario("308. Read session version for date validation — 200", this::case308),
        scenario("309. Cannot update session into past — 400", this::case309),
        scenario("310. Cannot reschedule session into past — 400", this::case310),
        scenario("311. Rejected changes preserve session — 200", this::case311),
        scenario("312. Create module at position 3 — 201", this::case312),
        scenario("313. Create module at position 4 — 201", this::case313),
        scenario("314. Read current module position and version — 200", this::case314),
        scenario("315. Move module forward with shift — 200", this::case315),
        scenario("316. Check module positions after forward move — 200", this::case316),
        scenario("317. Move module backward with shift — 200", this::case317),
        scenario("318. Check module positions after backward move — 200", this::case318),
        scenario("319. Reject stale version of shifted module — 409", this::case319),
        scenario("320. Delete temporary module at position 3 — 204", this::case320),
        scenario("321. Delete temporary module at position 4 — 204", this::case321),
        scenario("322. Create lesson at position 3 — 201", this::case322),
        scenario("323. Create lesson at position 4 — 201", this::case323),
        scenario("324. Read current lesson position and version — 200", this::case324),
        scenario("325. Move lesson forward with shift — 200", this::case325),
        scenario("326. Check lesson positions after forward move — 200", this::case326),
        scenario("327. Move lesson backward with shift — 200", this::case327),
        scenario("328. Check lesson positions after backward move — 200", this::case328),
        scenario("329. Reject stale version of shifted lesson — 409", this::case329),
        scenario("330. Delete temporary lesson at position 3 — 204", this::case330),
        scenario("331. Delete temporary lesson at position 4 — 204", this::case331),
        scenario("332. Gradebook first column page — 200", this::case332),
        scenario("333. Gradebook second column page — 200", this::case333),
        scenario("334. Gradebook quiz column page — 200", this::case334),
        scenario("335. Gradebook column page out of range — 200", this::case335),
        scenario("336. Gradebook filter counts only quiz columns — 200", this::case336),
        scenario("337. Reject invalid gradebook column size — 400", this::case337),
        scenario("338. Reject negative gradebook column page — 400", this::case338),
        scenario("339. Progress list returns summaries — 200", this::case339),
        scenario("340. Progress details first page — 200", this::case340),
        scenario("341. Progress details second page preserves totals — 200", this::case341),
        scenario("342. Progress details out of range preserve totals — 200", this::case342),
        scenario("343. Progress explicit summary for course — 200", this::case343),
        scenario("344. Reject unscoped progress details — 400", this::case344),
        scenario("345. Reject invalid progress detail size — 400", this::case345),
        scenario("346. Reject negative progress detail page — 400", this::case346),
        scenario("347. Progress detail page overflow rejected — 400", this::case347),
        scenario("348. Anonymous liveness", this::case348),
        scenario("349. Anonymous readiness", this::case349),
        scenario("350. Health component details denied", this::case350),
        scenario("351. Anonymous metrics denied", this::case351),
        scenario("352. Student metrics denied", this::case352),
        scenario("353. Anonymous individual metric denied", this::case353),
        scenario("354. Admin metrics names", this::case354),
        scenario("355. Admin counter courseflow.password.reset.requests", this::case355),
        scenario("356. Admin counter courseflow.password.reset.accepted", this::case356),
        scenario("357. Admin counter courseflow.auth.rate_limited", this::case357),
        scenario("358. Admin environment endpoint denied", this::case358),
        scenario("359. Register rejects 74 UTF-8 bytes — 400", this::case359),
        scenario("360. Admin create rejects 74 UTF-8 bytes — 400", this::case360),
        scenario("361. Login rejects 74 UTF-8 bytes — 400", this::case361),
        scenario("362. Change rejects oversized current password — 400", this::case362),
        scenario("363. Change rejects oversized new password — 400", this::case363),
        scenario("364. Reset rejects oversized new password — 400", this::case364),
        scenario("365. Register accepts exactly 72 UTF-8 bytes — 201", this::case365),
        scenario("366. Admin cannot create same normalized email — 409", this::case366),
        scenario("367. Login accepts exactly 72 UTF-8 bytes — 200", this::case367),
        scenario("368. Deactivate UTF8 test user — 200", this::case368),
        scenario("369. Clean up UTF8 test user — 204", this::case369),
        scenario("370. Missing submission filter retains existing behavior — 200", this::case370),
        scenario("371. Future unsubmitted assignments are not overdue — 200", this::case371),
        scenario("372. ADMIN may filter overdue assignments — 200", this::case372),
        scenario(
            "373. Overdue assignment filter cannot be applied to quiz-only gradebook — 400",
            this::case373),
        scenario("374. Student cannot use overdue gradebook filter — 403", this::case374),
        scenario("375. Student cannot extend deadline — 403", this::case375),
        scenario("376. Deadline extension requires version — 400", this::case376),
        scenario("377. Cannot shorten deadline — 400", this::case377),
        scenario("378. Cannot extend into the past — 400", this::case378),
        scenario("379. Teacher extends deadline after grading — 200", this::case379),
        scenario("380. Stale deadline update rejected — 409", this::case380),
        scenario("381. Admin extends deadline — 200", this::case381),
        scenario("382. Existing grade unchanged after deadline extension — 200", this::case382),
        scenario("383. Teacher regrades an already graded submission — 200", this::case383),
        scenario("384. Stale regrade cannot overwrite committed grade — 409", this::case384),
        scenario("385. Student cannot regrade — 403", this::case385),
        scenario("386. Rejected grades do not modify submission — 200", this::case386),
        scenario("387. Admin restores original grade — 200", this::case387),
        scenario("388. Register temporary authorization test user — 201", this::case388),
        scenario("389. Login temporary authorization test user — 200", this::case389),
        scenario("390. Active user with wrong current password — 400", this::case390),
        scenario("391. Deactivate temporary authorization test user — 200", this::case391),
        scenario("392. Inactive user cannot change password with revoked JWT — 401", this::case392),
        scenario("393. Inactive user cannot log in with correct password — 401", this::case393),
        scenario("394. Delete temporary authorization test user — 204", this::case394));
  }

  private DynamicTest scenario(String name, Executable action) {
    return DynamicTest.dynamicTest(
        name,
        () -> {
          Assumptions.assumeFalse(failed, "A prerequisite regression step failed");
          try {
            action.execute();
          } catch (Throwable failure) {
            failed = true;
            throw failure;
          }
        });
  }

  private String oversizedAnswers() {
    var answers = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < 201; i++) {
      answers.add(
          Map.of("questionId", Long.parseLong(get("question1_id")), "optionIds", List.of()));
    }
    return json(answers);
  }

  private void case001() throws Exception {
    initializeVariables();
    var response =
        request(
            "POST",
            "/api/auth/login",
            """
                {
                  "email": "{{admin_email}}",
                  "password": "{{admin_password}}"
                }
                """,
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(typeOf(field(response.body(), "accessToken")), "string"),
        "typeof response.body.accessToken === \"string\"");
    if (eq(response.status(), 200)) {
      set("admin_token", str(field(response.body(), "accessToken")));
      set("admin_id", str(field(response.body(), "userId")));
    }
  }

  private void case002() throws Exception {
    var response =
        request("GET", "/api/system/status", null, "Authorization", "Bearer {{admin_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "application"), "CourseFlow"),
        "response.body.application === \"CourseFlow\"");
    verify(eq(field(response.body(), "status"), "UP"), "response.body.status === \"UP\"");
    verify(
        eq(field(response.body(), "database"), "CONNECTED"),
        "response.body.database === \"CONNECTED\"");
    verify(
        truth(matches(Pattern.compile("^[0-9a-f-]{36}$"), response.header("X-Request-Id"))),
        "/^[0-9a-f-]{36}$/.test(response.headers.valueOf(\"X-Request-Id\"))");
  }

  private void case003() throws Exception {
    var response = request("GET", "/actuator/health", null);
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "status"), "UP"), "response.body.status === \"UP\"");
    verify(
        looseEq(field(response.body(), "components"), null), "Health must not disclose components");
    verify(looseEq(field(response.body(), "details"), null), "Health must not disclose details");
    verify(
        truth(matches(Pattern.compile("^[0-9a-f-]{36}$"), response.header("X-Request-Id"))),
        "/^[0-9a-f-]{36}$/.test(response.headers.valueOf(\"X-Request-Id\"))");
  }

  private void case004() throws Exception {
    var response =
        request(
            "OPTIONS",
            "/api/users/me",
            null,
            "Origin",
            "http://localhost:5173",
            "Access-Control-Request-Method",
            "GET",
            "Access-Control-Request-Headers",
            "authorization");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(response.header("Access-Control-Allow-Origin"), "http://localhost:5173"),
        "response.headers.valueOf(\"Access-Control-Allow-Origin\") === \"http://localhost:5173\"");
  }

  private void case005() throws Exception {
    var response =
        request(
            "OPTIONS",
            "/api/users/me",
            null,
            "Origin",
            "https://untrusted.example",
            "Access-Control-Request-Method",
            "GET",
            "Access-Control-Request-Headers",
            "authorization");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case006() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/register",
            """
                {
                  "displayName": "Regression student {{run_id}}",
                  "email": "{{student_email}}",
                  "password": "{{test_password}}"
                }
                """,
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    verify(
        truth(includes(field(response.body(), "roles"), "STUDENT")),
        "response.body.roles.includes(\"STUDENT\")");
    if (eq(response.status(), 201)) {
      set("student_id", str(field(response.body(), "id")));
    }
  }

  private void case007() throws Exception {
    long student = scenarioId("student_id");
    String email = get("student_email");
    verify(dbCount("SELECT count(*) FROM users WHERE email = ?", email) == 1, "Registration precondition");
    verify(dbCount("SELECT count(*) FROM user_accounts WHERE user_id = ?", student) == 1, "Account precondition");
    var response =
        request(
            "POST",
            "/api/auth/register",
            """
                {
                  "displayName": "Regression student {{run_id}}",
                  "email": "{{student_email}}",
                  "password": "{{test_password}}"
                }
                """,
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
    checkError(response, 409, "BUSINESS_CONFLICT");
    verify(dbCount("SELECT count(*) FROM users WHERE email = ?", email) == 1, "Duplicate user created");
    verify(dbCount("SELECT count(*) FROM user_accounts WHERE user_id = ?", student) == 1, "Duplicate account created");
  }

  private void case008() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/login",
            """
                {
                  "email": "{{student_email}}",
                  "password": "WrongPassword2026!"
                }
                """,
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 401), plus("Expected 401, got ", response.status()));
    verify(
        eq(field(response.body(), "code"), "INVALID_CREDENTIALS"),
        "response.body.code === \"INVALID_CREDENTIALS\"");
    verify(
        eq(typeOf(field(response.body(), "requestId")), "string"),
        "typeof response.body.requestId === \"string\"");
    verify(
        eq(field(response.body(), "requestId"), response.header("X-Request-Id")),
        "response.body.requestId === response.headers.valueOf(\"X-Request-Id\")");
  }

  private void case009() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/login",
            """
                {
                  "email": "{{student_email}}",
                  "password": "{{test_password}}"
                }
                """,
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(typeOf(field(response.body(), "accessToken")), "string"),
        "typeof response.body.accessToken === \"string\"");
    if (eq(response.status(), 200)) {
      set("student_token", str(field(response.body(), "accessToken")));
      set("student_id", str(field(response.body(), "userId")));
    }
  }

  private void case010() throws Exception {
    var response =
        request("GET", "/api/users/me", null, "Authorization", "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(str(field(response.body(), "id")), get("student_id")),
        "String(response.body.id) === client.global.get(\"student_id\")");
    verify(
        truth(includes(field(response.body(), "roles"), "STUDENT")),
        "response.body.roles.includes(\"STUDENT\")");
  }

  private void case011() throws Exception {
    var response = request("GET", "/api/users/me", null);
    verify(eq(response.status(), 401), plus("Expected 401, got ", response.status()));
  }

  private void case012() throws Exception {
    var response =
        request("GET", "/api/users/me", null, "Authorization", "Bearer invalid.jwt.token");
    verify(eq(response.status(), 401), plus("Expected 401, got ", response.status()));
  }

  private void case013() throws Exception {
    var response = request("GET", "/api/users", null, "Authorization", "Bearer {{student_token}}");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
    verify(
        eq(field(response.body(), "code"), "ACCESS_DENIED"),
        "response.body.code === \"ACCESS_DENIED\"");
    verify(
        eq(typeOf(field(response.body(), "requestId")), "string"),
        "typeof response.body.requestId === \"string\"");
    verify(
        eq(field(response.body(), "requestId"), response.header("X-Request-Id")),
        "response.body.requestId === response.headers.valueOf(\"X-Request-Id\")");
  }

  private void case014() throws Exception {
    var response =
        request(
            "GET", "/api/users/{{admin_id}}", null, "Authorization", "Bearer {{student_token}}");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case015() throws Exception {
    var response =
        request(
            "POST",
            "/api/users",
            """
                {
                  "displayName": "Regression teacher {{run_id}}",
                  "email": "{{teacher_email}}",
                  "password": "{{test_password}}",
                  "passwordChangeRequired": false,
                  "roles": [
                    "STUDENT",
                    "TEACHER"
                  ]
                }
                """,
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("teacher_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    if (eq(response.status(), 201)) {
      set("teacher_id", str(field(response.body(), "id")));
    }
  }

  private void case016() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/login",
            """
                {
                  "email": "{{teacher_email}}",
                  "password": "{{test_password}}"
                }
                """,
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(typeOf(field(response.body(), "accessToken")), "string"),
        "typeof response.body.accessToken === \"string\"");
    if (eq(response.status(), 200)) {
      set("teacher_token", str(field(response.body(), "accessToken")));
      set("teacher_id", str(field(response.body(), "userId")));
    }
  }

  private void case017() throws Exception {
    var response =
        request(
            "POST",
            "/api/users",
            """
                {
                  "displayName": "Regression other {{run_id}}",
                  "email": "{{other_email}}",
                  "password": "{{test_password}}",
                  "passwordChangeRequired": false,
                  "roles": [
                    "STUDENT"
                  ]
                }
                """,
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("other_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    if (eq(response.status(), 201)) {
      set("other_id", str(field(response.body(), "id")));
    }
  }

  private void case018() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/login",
            """
                {
                  "email": "{{other_email}}",
                  "password": "{{test_password}}"
                }
                """,
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(typeOf(field(response.body(), "accessToken")), "string"),
        "typeof response.body.accessToken === \"string\"");
    if (eq(response.status(), 200)) {
      set("other_token", str(field(response.body(), "accessToken")));
      set("other_id", str(field(response.body(), "userId")));
    }
  }

  private void case019() throws Exception {
    var response =
        request("GET", "/api/users/{{other_id}}", null, "Authorization", "Bearer {{admin_token}}");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("other_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(str(field(response.body(), "id")), get("other_id")),
        "String(response.body.id) === client.global.get(\"other_id\")");
  }

  private void case020() throws Exception {
    var response =
        request(
            "GET",
            "/api/users?q={{run_id}}&status=ACTIVE&role=STUDENT&page=0&size=10&sortBy=displayName&direction=asc",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        truth(
            some(
                field(response.body(), "content"),
                x -> eq(str(field(x, "id")), get("student_id")))),
        "response.body.content.some(x => String(x.id) === client.global.get(\"student_id\"))");
    verify(
        truth(
            some(
                field(response.body(), "content"),
                x -> eq(str(field(x, "id")), get("teacher_id")))),
        "response.body.content.some(x => String(x.id) === client.global.get(\"teacher_id\"))");
    verify(
        truth(
            some(field(response.body(), "content"), x -> eq(str(field(x, "id")), get("other_id")))),
        "response.body.content.some(x => String(x.id) === client.global.get(\"other_id\"))");
    verify(eq(field(response.body(), "totalElements"), 3), "response.body.totalElements === 3");
  }

  private void case021() throws Exception {
    var response =
        request(
            "PUT",
            "/api/users/{{other_id}}",
            """
                {"version": {{other_version}},
                  "displayName": "Regression other updated {{run_id}}",
                  "email": "{{other_updated_email}}"
                }
                """,
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("other_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "email"), get("other_updated_email")),
        "response.body.email === client.global.get(\"other_updated_email\")");
  }

  private void case022() throws Exception {
    var response = request("GET", "/api/users/me", null, "Authorization", "Bearer {{other_token}}");
    verify(eq(response.status(), 401), plus("Expected 401, got ", response.status()));
  }

  private void case023() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/login",
            """
                {
                  "email": "{{other_updated_email}}",
                  "password": "{{test_password}}"
                }
                """,
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(typeOf(field(response.body(), "accessToken")), "string"),
        "typeof response.body.accessToken === \"string\"");
    if (eq(response.status(), 200)) {
      set("other_token", str(field(response.body(), "accessToken")));
      set("other_id", str(field(response.body(), "userId")));
    }
  }

  private void case024() throws Exception {
    var response =
        request(
            "PUT",
            "/api/users/{{other_id}}/roles",
            """
                {"version": {{other_version}},
                  "roles": [
                    "STUDENT",
                    "TEACHER"
                  ]
                }
                """,
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("other_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        truth(includes(field(response.body(), "roles"), "TEACHER")),
        "response.body.roles.includes(\"TEACHER\")");
  }

  private void case025() throws Exception {
    var response = request("GET", "/api/users/me", null, "Authorization", "Bearer {{other_token}}");
    verify(eq(response.status(), 401), plus("Expected 401, got ", response.status()));
  }

  private void case026() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/login",
            """
                {
                  "email": "{{other_updated_email}}",
                  "password": "{{test_password}}"
                }
                """,
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(typeOf(field(response.body(), "accessToken")), "string"),
        "typeof response.body.accessToken === \"string\"");
    if (eq(response.status(), 200)) {
      set("other_token", str(field(response.body(), "accessToken")));
      set("other_id", str(field(response.body(), "userId")));
    }
  }

  private void case027() throws Exception {
    var response =
        request(
            "PUT",
            "/api/users/{{other_id}}/roles",
            """
                {"version": {{other_version}},
                  "roles": []
                }
                """,
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case028() throws Exception {
    var response =
        request(
            "POST",
            "/api/users/{{admin_id}}/deactivate",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case029() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/change-password",
            """
                {
                  "currentPassword": "{{test_password}}",
                  "newPassword": "{{new_password}}"
                }
                """,
            "Authorization",
            "Bearer {{student_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case030() throws Exception {
    var response =
        request("GET", "/api/users/me", null, "Authorization", "Bearer {{student_token}}");
    verify(eq(response.status(), 401), plus("Expected 401, got ", response.status()));
  }

  private void case031() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/login",
            """
                {
                  "email": "{{student_email}}",
                  "password": "{{test_password}}"
                }
                """,
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 401), plus("Expected 401, got ", response.status()));
  }

  private void case032() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/login",
            """
                {
                  "email": "{{student_email}}",
                  "password": "{{new_password}}"
                }
                """,
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(typeOf(field(response.body(), "accessToken")), "string"),
        "typeof response.body.accessToken === \"string\"");
    if (eq(response.status(), 200)) {
      set("student_token", str(field(response.body(), "accessToken")));
      set("student_id", str(field(response.body(), "userId")));
    }
  }

  private void case033() throws Exception {
    var response =
        request("POST", "/api/auth/logout", null, "Authorization", "Bearer {{student_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case034() throws Exception {
    var response =
        request("GET", "/api/users/me", null, "Authorization", "Bearer {{student_token}}");
    verify(eq(response.status(), 401), plus("Expected 401, got ", response.status()));
  }

  private void case035() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/login",
            """
                {
                  "email": "{{student_email}}",
                  "password": "{{new_password}}"
                }
                """,
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(typeOf(field(response.body(), "accessToken")), "string"),
        "typeof response.body.accessToken === \"string\"");
    if (eq(response.status(), 200)) {
      set("student_token", str(field(response.body(), "accessToken")));
      set("student_id", str(field(response.body(), "userId")));
    }
  }

  private void case036() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/forgot-password",
            """
                {
                  "email": "reg.unknown.{{run_id}}@courseflow.local"
                }
                """,
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 202), plus("Expected 202, got ", response.status()));
  }

  private void case037() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/reset-password",
            """
                {
                  "token": "invalid-{{run_id}}",
                  "newPassword": "{{test_password}}"
                }
                """,
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case038() throws Exception {
    var response =
        request(
            "POST",
            "/api/users/999999999/password-reset",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 404), plus("Expected 404, got ", response.status()));
  }

  private void case039() throws Exception {
    var response =
        request(
            "POST",
            "/api/categories",
            """
                {
                  "name": "Regression {{run_id}}",
                  "description": "Regression category"
                }
                """,
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    if (eq(response.status(), 201)) {
      set("category_id", str(field(response.body(), "id")));
      set("category_version", str(field(response.body(), "version")));
    }
  }

  private void case040() throws Exception {
    var response =
        request(
            "POST",
            "/api/categories",
            """
                {
                  "name": "Regression {{run_id}}",
                  "description": "Regression category"
                }
                """,
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case041() throws Exception {
    var response =
        request(
            "POST",
            "/api/categories",
            """
                {
                  "name": "Regression {{run_id}}",
                  "description": "Regression category"
                }
                """,
            "Authorization",
            "Bearer {{student_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case042() throws Exception {
    var response =
        request(
            "POST",
            "/api/categories",
            """
                {
                  "name": "",
                  "description": "Invalid"
                }
                """,
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
    verify(
        eq(field(response.body(), "code"), "VALIDATION_FAILED"),
        "response.body.code === \"VALIDATION_FAILED\"");
    verify(
        eq(typeOf(field(response.body(), "requestId")), "string"),
        "typeof response.body.requestId === \"string\"");
    verify(
        eq(field(response.body(), "requestId"), response.header("X-Request-Id")),
        "response.body.requestId === response.headers.valueOf(\"X-Request-Id\")");
  }

  private void case043() throws Exception {
    var response =
        request(
            "GET",
            "/api/categories?search=Regression%20{{run_id}}&page=0&size=5&sortBy=name&direction=desc",
            null);
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        truth(
            some(
                field(response.body(), "content"),
                x -> eq(str(field(x, "id")), get("category_id")))),
        "response.body.content.some(x => String(x.id) === client.global.get(\"category_id\"))");
  }

  private void case044() throws Exception {
    var response = request("GET", "/api/categories/{{category_id}}", null);
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(str(field(response.body(), "id")), get("category_id")),
        "String(response.body.id) === client.global.get(\"category_id\")");
  }

  private void case045() throws Exception {
    var response = request("GET", "/api/categories?size=0", null);
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case046() throws Exception {
    var response = request("GET", "/api/categories/999999999", null);
    verify(eq(response.status(), 404), plus("Expected 404, got ", response.status()));
    verify(
        eq(field(response.body(), "code"), "RESOURCE_NOT_FOUND"),
        "response.body.code === \"RESOURCE_NOT_FOUND\"");
    verify(
        eq(typeOf(field(response.body(), "requestId")), "string"),
        "typeof response.body.requestId === \"string\"");
    verify(
        eq(field(response.body(), "requestId"), response.header("X-Request-Id")),
        "response.body.requestId === response.headers.valueOf(\"X-Request-Id\")");
  }

  private void case047() throws Exception {
    var response =
        request(
            "POST",
            "/api/tags",
            """
                {
                  "name": "reg-{{run_id}}"
                }
                """,
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    if (eq(response.status(), 201)) {
      set("tag_id", str(field(response.body(), "id")));
      set("tag_version", str(field(response.body(), "version")));
    }
  }

  private void case048() throws Exception {
    var response =
        request(
            "PUT",
            "/api/tags/{{tag_id}}",
            """
                {
                  "version": {{tag_version}}, "name": "reg-updated-{{run_id}}"
                }
                """,
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "name"), plus("reg-updated-", get("run_id"))),
        "response.body.name === \"reg-updated-\" + client.global.get(\"run_id\")");
  }

  private void case049() throws Exception {
    var response = request("GET", "/api/tags/{{tag_id}}", null);
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(str(field(response.body(), "id")), get("tag_id")),
        "String(response.body.id) === client.global.get(\"tag_id\")");
  }

  private void case050() throws Exception {
    var response = request("GET", "/api/tags?page=0&size=10&q=reg-updated-{{run_id}}", null);
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        truth(some(field(response.body(), "content"), x -> eq(str(field(x, "id")), get("tag_id")))),
        "response.body.content.some(x => String(x.id) === client.global.get(\"tag_id\"))");
  }

  private void case051() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses",
            """
                {
                  "title": "Regression course {{run_id}}",
                  "description": "Regression course",
                  "categoryId": {{category_id}},
                  "teacherId": {{teacher_id}},
                  "duration": 56,
                  "startDate": "{{course_start}}"
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("course_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    verify(
        eq(str(field(response.body(), "teacherId")), get("teacher_id")),
        "String(response.body.teacherId) === client.global.get(\"teacher_id\")");
    if (eq(response.status(), 201)) {
      set("course_id", str(field(response.body(), "id")));
    }
  }

  private void case052() throws Exception {
    var response =
        request(
            "PUT",
            "/api/courses/{{course_id}}",
            """
                {"version": {{course_version}},
                  "title": "Regression updated course {{run_id}}",
                  "description": "Regression course",
                  "categoryId": {{category_id}},
                  "teacherId": {{teacher_id}},
                  "duration": 56,
                  "startDate": "{{course_start}}"
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("course_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "title"), plus("Regression updated course ", get("run_id"))),
        "response.body.title === \"Regression updated course \" + client.global.get(\"run_id\")");
  }

  private void case053() throws Exception {
    var response = request("GET", "/api/courses/{{course_id}}", null);
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("course_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(str(field(response.body(), "id")), get("course_id")),
        "String(response.body.id) === client.global.get(\"course_id\")");
  }

  private void case054() throws Exception {
    var response = request("GET", "/api/courses?q=%25&categoryId={{category_id}}", null);
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "totalElements"), 0), "response.body.totalElements === 0");
  }

  private void case055() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses?q={{run_id}}&categoryId={{category_id}}&teacherId={{teacher_id}}&status=ACTIVE&page=0&size=10",
            null);
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        truth(
            some(
                field(response.body(), "content"), x -> eq(str(field(x, "id")), get("course_id")))),
        "response.body.content.some(x => String(x.id) === client.global.get(\"course_id\"))");
  }

  private void case056() throws Exception {
    var response =
        request(
            "PUT",
            "/api/courses/{{course_id}}",
            """
                {"version": {{course_version}},
                  "title": "Forbidden {{run_id}}",
                  "description": "Regression course",
                  "categoryId": {{category_id}},
                  "teacherId": {{teacher_id}},
                  "duration": 56,
                  "startDate": "{{course_start}}"
                }
                """,
            "Authorization",
            "Bearer {{other_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case057() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses",
            """
                {
                  "title": "Regression temporary course {{run_id}}",
                  "description": "Regression course",
                  "categoryId": {{category_id}},
                  "teacherId": {{other_id}},
                  "duration": 56,
                  "startDate": "{{course_start}}"
                }
                """,
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("temp_course_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    if (eq(response.status(), 201)) {
      set("temp_course_id", str(field(response.body(), "id")));
    }
  }

  private void case058() throws Exception {
    var response =
        request(
            "PUT",
            "/api/courses/{{temp_course_id}}/teacher",
            """
                {"version": {{temp_course_version}},
                  "teacherId": {{teacher_id}}
                }
                """,
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("temp_course_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(str(field(response.body(), "teacherId")), get("teacher_id")),
        "String(response.body.teacherId) === client.global.get(\"teacher_id\")");
  }

  private void case059() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{temp_course_id}}/archive",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "status"), "ARCHIVED"), "response.body.status === \"ARCHIVED\"");
  }

  private void case060() throws Exception {
    long course = scenarioId("temp_course_id");
    long student = scenarioId("student_id");
    long before = dbCount("SELECT count(*) FROM enrollments WHERE course_id = ? AND student_id = ?", course, student);
    var response =
        request(
            "POST",
            "/api/courses/{{temp_course_id}}/enrollments",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
    checkError(response, 409, "BUSINESS_CONFLICT");
    verify(dbCount("SELECT count(*) FROM enrollments WHERE course_id = ? AND student_id = ?", course, student) == before, "Rejected enrollment changed database");
  }

  private void case061() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/courses/{{temp_course_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case062() throws Exception {
    var response = request("GET", "/api/courses/{{temp_course_id}}", null);
    verify(eq(response.status(), 404), plus("Expected 404, got ", response.status()));
  }

  private void case063() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{course_id}}/tags/{{tag_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case064() throws Exception {
    var response = request("GET", "/api/courses/{{course_id}}/tags", null);
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        truth(some(field(response.body(), "content"), x -> eq(str(field(x, "id")), get("tag_id")))),
        "response.body.content.some(x => String(x.id) === client.global.get(\"tag_id\"))");
  }

  private void case065() throws Exception {
    var response =
        request("DELETE", "/api/tags/{{tag_id}}", null, "Authorization", "Bearer {{admin_token}}");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case066() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/courses/{{course_id}}/tags/{{tag_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case067() throws Exception {
    var response =
        request("DELETE", "/api/tags/{{tag_id}}", null, "Authorization", "Bearer {{admin_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case068() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{course_id}}/modules",
            """
                {
                  "title": "Java fundamentals",
                  "description": "Java syntax",
                  "position": 1
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("module_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    if (eq(response.status(), 201)) {
      set("module_id", str(field(response.body(), "id")));
    }
  }

  private void case069() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{course_id}}/modules",
            """
                {
                  "title": "Java fundamentals",
                  "description": "Java syntax",
                  "position": 1
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case070() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{course_id}}/modules",
            """
                {
                  "title": "Temporary module",
                  "description": "Delete after test",
                  "position": 2
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    if (eq(response.status(), 201)) {
      set("temp_module_id", str(field(response.body(), "id")));
    }
  }

  private void case071() throws Exception {
    var response =
        request(
            "PUT",
            "/api/courses/{{course_id}}/modules/{{module_id}}",
            """
                {"version": {{module_version}},
                  "title": "Java basics",
                  "description": "Updated module"
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("module_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "title"), "Java basics"),
        "response.body.title === \"Java basics\"");
  }

  private void case072() throws Exception {
    var response =
        request(
            "PATCH",
            "/api/courses/{{course_id}}/modules/{{module_id}}/position",
            """
                {"version": {{module_version}},
                  "position": 2
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("module_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "position"), 2), "response.body.position === 2");
  }

  private void case073() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/modules/{{module_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "position"), 2), "response.body.position === 2");
  }

  private void case074() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/modules",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        truth(
            some(
                field(response.body(), "content"), x -> eq(str(field(x, "id")), get("module_id")))),
        "response.body.content.some(x => String(x.id) === client.global.get(\"module_id\"))");
    verify(
        truth(
            some(
                field(response.body(), "content"),
                x -> eq(str(field(x, "id")), get("temp_module_id")))),
        "response.body.content.some(x => String(x.id) === client.global.get(\"temp_module_id\"))");
  }

  private void case075() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{course_id}}/modules/{{module_id}}/lessons",
            """
                {
                  "title": "Introduction to Java",
                  "content": "JVM, JDK and JRE",
                  "videoUrl": "https://example.org/java",
                  "position": 1
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("lesson_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    if (eq(response.status(), 201)) {
      set("lesson_id", str(field(response.body(), "id")));
    }
  }

  private void case076() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{course_id}}/modules/{{module_id}}/lessons",
            """
                {
                  "title": "Temporary lesson",
                  "content": "Test",
                  "position": 2
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    if (eq(response.status(), 201)) {
      set("temp_lesson_id", str(field(response.body(), "id")));
    }
  }

  private void case077() throws Exception {
    var response =
        request(
            "PUT",
            "/api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{lesson_id}}",
            """
                {"version": {{lesson_version}},
                  "title": "Introduction to Java",
                  "content": "Java fundamentals and types",
                  "videoUrl": "https://example.org/java-updated"
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("lesson_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
  }

  private void case078() throws Exception {
    var response =
        request(
            "PUT",
            "/api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{lesson_id}}",
            "{\"version\": {{lesson_version}},\"title\":\"Invalid video\",\"content\":\"Test\",\"videoUrl\":\"javascript:alert(1)\"}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case079() throws Exception {
    set("oversized_content", "x".repeat(20001));
    var response =
        request(
            "PUT",
            "/api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{lesson_id}}",
            "{\"version\": {{lesson_version}},\"title\":\"Invalid content\",\"content\":\"{{oversized_content}}\"}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case080() throws Exception {
    var response =
        request(
            "PATCH",
            "/api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{lesson_id}}/position",
            """
                {"version": {{lesson_version}},
                  "position": 2
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("lesson_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "position"), 2), "response.body.position === 2");
  }

  private void case081() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{temp_lesson_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case082() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{lesson_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(str(field(response.body(), "id")), get("lesson_id")),
        "String(response.body.id) === client.global.get(\"lesson_id\")");
    verify(
        eq(typeOf(field(response.body(), "content")), "string"),
        "typeof response.body.content === \"string\"");
    verify(
        eq(typeOf(field(response.body(), "videoUrl")), "string"),
        "typeof response.body.videoUrl === \"string\"");
    verify(
        eq(typeOf(field(response.body(), "version")), "number"),
        "typeof response.body.version === \"number\"");
  }

  private void case083() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/modules/{{module_id}}/lessons",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        truth(
            some(
                field(response.body(), "content"), x -> eq(str(field(x, "id")), get("lesson_id")))),
        "response.body.content.some(x => String(x.id) === client.global.get(\"lesson_id\"))");
    verify(
        truth(
            every(
                field(response.body(), "content"),
                x -> (!truth(has(x, "content")) && !truth(has(x, "videoUrl"))))),
        "response.body.content.every(x => !Object.prototype.hasOwnProperty.call(x, \"content\") && !Object.prototype.hasOwnProperty.call(x, \"videoUrl\"))");
    verify(
        truth(
            every(
                field(response.body(), "content"), x -> eq(typeOf(field(x, "version")), "number"))),
        "response.body.content.every(x => typeof x.version === \"number\")");
  }

  private void case084() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/modules/{{module_id}}/lessons",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case085() throws Exception {
    var response =
        request(
            "POST",
            "/api/lessons/{{lesson_id}}/sessions",
            """
                {
                  "startsAt": "{{session_start}}",
                  "endsAt": "{{session_end}}",
                  "format": "ONLINE",
                  "meetingUrl": "https://example.org/meeting/java"
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("session_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    if (eq(response.status(), 201)) {
      set("session_id", str(field(response.body(), "id")));
    }
  }

  private void case086() throws Exception {
    var response =
        request(
            "POST",
            "/api/lessons/{{lesson_id}}/sessions",
            """
                {
                  "startsAt": "{{session_start}}",
                  "endsAt": "{{session_end}}",
                  "format": "ONLINE",
                  "meetingUrl": "https://example.org/meeting/java"
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case087() throws Exception {
    var response =
        request(
            "PUT",
            "/api/lessons/{{lesson_id}}/sessions/{{session_id}}",
            """
                {"version": {{session_version}},
                  "startsAt": "{{session_start}}",
                  "endsAt": "{{session_end}}",
                  "format": "ONLINE",
                  "meetingUrl": "https://example.org/meeting/java-updated"
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("session_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "meetingUrl"), "https://example.org/meeting/java-updated"),
        "response.body.meetingUrl === \"https://example.org/meeting/java-updated\"");
  }

  private void case088() throws Exception {
    var response =
        request(
            "PATCH",
            "/api/lessons/{{lesson_id}}/sessions/{{session_id}}/time",
            """
                {"version": {{session_version}},
                  "startsAt": "{{session_new_start}}",
                  "endsAt": "{{session_new_end}}"
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("session_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(instant(field(response.body(), "startsAt")), instant(get("session_new_start"))),
        "Date.parse(response.body.startsAt) === Date.parse(client.global.get(\"session_new_start\"))");
  }

  private void case089() throws Exception {
    var response =
        request(
            "GET",
            "/api/lessons/{{lesson_id}}/sessions/{{session_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(str(field(response.body(), "id")), get("session_id")),
        "String(response.body.id) === client.global.get(\"session_id\")");
  }

  private void case090() throws Exception {
    var response =
        request(
            "GET",
            "/api/lessons/{{lesson_id}}/sessions",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        truth(
            some(
                field(response.body(), "content"),
                x -> eq(str(field(x, "id")), get("session_id")))),
        "response.body.content.some(x => String(x.id) === client.global.get(\"session_id\"))");
  }

  private void case091() throws Exception {
    var response =
        request(
            "POST",
            "/api/lessons/{{lesson_id}}/sessions",
            "{\"startsAt\":\"{{temp_session_start}}\",\"endsAt\":\"{{temp_session_end}}\",\"format\":\"ONLINE\"}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case092() throws Exception {
    var response =
        request(
            "POST",
            "/api/lessons/{{lesson_id}}/sessions",
            "{\"startsAt\":\"{{temp_session_start}}\",\"endsAt\":\"{{temp_session_end}}\",\"format\":\"ONLINE\",\"meetingUrl\":\"javascript:alert(1)\"}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case093() throws Exception {
    var response =
        request(
            "POST",
            "/api/lessons/{{lesson_id}}/sessions",
            "{\"startsAt\":\"{{temp_session_start}}\",\"endsAt\":\"{{temp_session_end}}\",\"format\":\"OFFLINE\",\"meetingUrl\":\"https://example.org/meeting\"}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case094() throws Exception {
    var response =
        request(
            "POST",
            "/api/lessons/{{lesson_id}}/sessions",
            """
                {
                  "startsAt": "{{temp_session_start}}",
                  "endsAt": "{{temp_session_end}}",
                  "format": "OFFLINE"
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    if (eq(response.status(), 201)) {
      set("temp_session_id", str(field(response.body(), "id")));
    }
  }

  private void case095() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/lessons/{{lesson_id}}/sessions/{{temp_session_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case096() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{course_id}}/enrollments",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    verify(eq(field(response.body(), "status"), "ACTIVE"), "response.body.status === \"ACTIVE\"");
    verify(
        eq(
            response.header("Location"),
            plus(plus("/api/courses/", get("course_id")), "/enrollments/me")),
        "response.headers.valueOf(\"Location\") === \"/api/courses/\" + client.global.get(\"course_id\") + \"/enrollments/me\"");
    if (eq(response.status(), 201)) {
      set("enrollment_id", str(field(response.body(), "id")));
    }
  }

  private void case097() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{course_id}}/enrollments",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(str(field(response.body(), "id")), get("enrollment_id")),
        "String(response.body.id) === client.global.get(\"enrollment_id\")");
  }

  private void case098() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{course_id}}/enrollments",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case099() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/enrollments/me",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(str(field(response.body(), "id")), get("enrollment_id")),
        "String(response.body.id) === client.global.get(\"enrollment_id\")");
  }

  private void case100() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/enrollments?status=ACTIVE&page=0&size=10",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        truth(
            some(
                field(response.body(), "content"),
                x -> eq(str(field(x, "studentId")), get("student_id")))),
        "response.body.content.some(x => String(x.studentId) === client.global.get(\"student_id\"))");
  }

  private void case101() throws Exception {
    var response =
        request(
            "GET",
            "/api/users/me/enrollments?status=ACTIVE",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        truth(
            some(
                field(response.body(), "content"),
                x -> eq(str(field(x, "courseId")), get("course_id")))),
        "response.body.content.some(x => String(x.courseId) === client.global.get(\"course_id\"))");
  }

  private void case102() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/modules",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        truth(
            some(
                field(response.body(), "content"), x -> eq(str(field(x, "id")), get("module_id")))),
        "response.body.content.some(x => String(x.id) === client.global.get(\"module_id\"))");
  }

  private void case103() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/modules/{{module_id}}/lessons",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        truth(
            some(
                field(response.body(), "content"), x -> eq(str(field(x, "id")), get("lesson_id")))),
        "response.body.content.some(x => String(x.id) === client.global.get(\"lesson_id\"))");
  }

  private void case104() throws Exception {
    var response =
        request(
            "GET",
            "/api/lessons/{{lesson_id}}/sessions",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        truth(
            some(
                field(response.body(), "content"),
                x -> eq(str(field(x, "id")), get("session_id")))),
        "response.body.content.some(x => String(x.id) === client.global.get(\"session_id\"))");
  }

  private void case105() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/enrollments",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case106() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/enrollments",
            null,
            "Authorization",
            "Bearer {{other_token}}");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case107() throws Exception {
    var response =
        request(
            "POST",
            "/api/lessons/{{lesson_id}}/assignments",
            """
                {
                  "title": "Java homework 1",
                  "description": "Explain JVM, JDK and JRE",
                  "dueAt": "{{due_at}}",
                  "maxScore": 100
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("assignment_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    verify(
        eq(
            response.header("Location"),
            plus(
                plus(plus("/api/lessons/", get("lesson_id")), "/assignments/"),
                field(response.body(), "id"))),
        "response.headers.valueOf(\"Location\") === \"/api/lessons/\" + client.global.get(\"lesson_id\") + \"/assignments/\" + response.body.id");
    if (eq(response.status(), 201)) {
      set("assignment_id", str(field(response.body(), "id")));
    }
  }

  private void case108() throws Exception {
    var response =
        request(
            "PUT",
            "/api/lessons/{{lesson_id}}/assignments/{{assignment_id}}",
            """
                {"version": {{assignment_version}},
                  "title": "Java homework 1 updated",
                  "description": "Explain JVM, JDK and JRE",
                  "dueAt": "{{due_at}}",
                  "maxScore": 100
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("assignment_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "maxScore"), 100), "response.body.maxScore === 100");
  }

  private void case109() throws Exception {
    var response =
        request(
            "POST",
            "/api/lessons/{{lesson_id}}/assignments",
            """
                {
                  "title": "Java homework 2",
                  "description": "Explain primitive types",
                  "dueAt": "{{due_at}}",
                  "maxScore": 50
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    if (eq(response.status(), 201)) {
      set("assignment2_id", str(field(response.body(), "id")));
    }
  }

  private void case110() throws Exception {
    var response =
        request(
            "POST",
            "/api/lessons/{{lesson_id}}/assignments",
            """
                {
                  "title": "Temporary assignment",
                  "description": "Explain JVM, JDK and JRE",
                  "dueAt": "{{due_at}}",
                  "maxScore": 100
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    if (eq(response.status(), 201)) {
      set("temp_assignment_id", str(field(response.body(), "id")));
    }
  }

  private void case111() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/lessons/{{lesson_id}}/assignments/{{temp_assignment_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case112() throws Exception {
    var response =
        request(
            "GET",
            "/api/lessons/{{lesson_id}}/assignments/{{assignment_id}}",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(str(field(response.body(), "id")), get("assignment_id")),
        "String(response.body.id) === client.global.get(\"assignment_id\")");
  }

  private void case113() throws Exception {
    var response =
        request(
            "GET",
            "/api/lessons/{{lesson_id}}/assignments",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        truth(
            some(
                field(response.body(), "content"),
                x -> eq(str(field(x, "id")), get("assignment_id")))),
        "response.body.content.some(x => String(x.id) === client.global.get(\"assignment_id\"))");
    verify(
        truth(
            some(
                field(response.body(), "content"),
                x -> eq(str(field(x, "id")), get("assignment2_id")))),
        "response.body.content.some(x => String(x.id) === client.global.get(\"assignment2_id\"))");
    verify(eq(field(response.body(), "totalElements"), 2), "response.body.totalElements === 2");
  }

  private void case114() throws Exception {
    long assignment = scenarioId("assignment_id");
    long student = scenarioId("other_id");
    verify(dbCount("SELECT count(*) FROM submissions WHERE assignment_id = ? AND student_id = ?", assignment, student) == 0, "Precondition: no submission");
    var response =
        request(
            "POST",
            "/api/assignments/{{assignment_id}}/submissions",
            """
                {
                  "content": "Not enrolled"
                }
                """,
            "Authorization",
            "Bearer {{other_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
    checkError(response, 403, "ACCESS_DENIED");
    verify(dbCount("SELECT count(*) FROM submissions WHERE assignment_id = ? AND student_id = ?", assignment, student) == 0, "Forbidden submission persisted");
  }

  private void case115() throws Exception {
    var response =
        request(
            "POST",
            "/api/assignments/{{assignment_id}}/submissions",
            """
                {
                  "content": "JDK provides tools; JRE provides runtime; JVM executes bytecode."
                }
                """,
            "Authorization",
            "Bearer {{student_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("submission_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    verify(
        eq(field(response.body(), "status"), "SUBMITTED"),
        "response.body.status === \"SUBMITTED\"");
    verify(eq(field(response.body(), "score"), null), "response.body.score === null");
    verify(
        eq(
            response.header("Location"),
            plus(plus("/api/assignments/", get("assignment_id")), "/submissions/me")),
        "response.headers.valueOf(\"Location\") === \"/api/assignments/\" + client.global.get(\"assignment_id\") + \"/submissions/me\"");
    if (eq(response.status(), 201)) {
      set("submission_id", str(field(response.body(), "id")));
    }
  }

  private void case116() throws Exception {
    long submission = scenarioId("submission_id");
    var before = jdbc.queryForMap("SELECT * FROM submissions WHERE id = ?", submission);
    var response =
        request(
            "POST",
            "/api/assignments/{{assignment_id}}/submissions",
            """
                {
                  "content": "Duplicate"
                }
                """,
            "Authorization",
            "Bearer {{student_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
    verify(
        eq(field(response.body(), "code"), "BUSINESS_CONFLICT"),
        "response.body.code === \"BUSINESS_CONFLICT\"");
    verify(
        eq(typeOf(field(response.body(), "requestId")), "string"),
        "typeof response.body.requestId === \"string\"");
    verify(
        eq(field(response.body(), "requestId"), response.header("X-Request-Id")),
        "response.body.requestId === response.headers.valueOf(\"X-Request-Id\")");
    checkError(response, 409, "BUSINESS_CONFLICT");
    verify(dbCount("SELECT count(*) FROM submissions WHERE assignment_id = ? AND student_id = ?", scenarioId("assignment_id"), scenarioId("student_id")) == 1, "Duplicate submission persisted");
    verify(before.equals(jdbc.queryForMap("SELECT * FROM submissions WHERE id = ?", submission)), "Original submission changed");
  }

  private void case117() throws Exception {
    var response =
        request(
            "GET",
            "/api/assignments/{{assignment_id}}/submissions/me",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(str(field(response.body(), "id")), get("submission_id")),
        "String(response.body.id) === client.global.get(\"submission_id\")");
  }

  private void case118() throws Exception {
    var response =
        request(
            "GET",
            "/api/assignments/{{assignment_id}}/submissions?status=SUBMITTED",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        truth(
            some(
                field(response.body(), "content"),
                x -> eq(str(field(x, "id")), get("submission_id")))),
        "response.body.content.some(x => String(x.id) === client.global.get(\"submission_id\"))");
  }

  private void case119() throws Exception {
    var response =
        request(
            "GET",
            "/api/assignments/{{assignment_id}}/submissions",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case120() throws Exception {
    var response =
        request(
            "GET",
            "/api/assignments/{{assignment_id}}/submissions",
            null,
            "Authorization",
            "Bearer {{other_token}}");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case121() throws Exception {
    long submission = scenarioId("submission_id");
    var before = jdbc.queryForMap("SELECT * FROM submissions WHERE id = ?", submission);
    var response =
        request(
            "PUT",
            "/api/assignments/{{assignment_id}}/submissions/{{submission_id}}/grade",
            """
                {"version": {{submission_version}},
                  "score": 101,
                  "feedback": "Invalid"
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
    checkError(response, 400, "BAD_REQUEST");
    verify(before.equals(jdbc.queryForMap("SELECT * FROM submissions WHERE id = ?", submission)), "Invalid grade changed submission");
  }

  private void case122() throws Exception {
    var response =
        request("GET", "/api/dashboard/teacher", null, "Authorization", "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "pendingSubmissionCount"), 1),
        "response.body.pendingSubmissionCount === 1");
    verify(
        truth(
            some(
                field(response.body(), "pendingSubmissions"),
                x -> eq(str(field(x, "submissionId")), get("submission_id")))),
        "response.body.pendingSubmissions.some(x => String(x.submissionId) === client.global.get(\"submission_id\"))");
    verify(
        truth(
            some(
                field(response.body(), "courses"),
                x -> eq(str(field(x, "courseId")), get("course_id")))),
        "response.body.courses.some(x => String(x.courseId) === client.global.get(\"course_id\"))");
    verify(
        truth(
            some(
                field(response.body(), "upcomingSessions"),
                x -> eq(str(field(x, "sessionId")), get("session_id")))),
        "response.body.upcomingSessions.some(x => String(x.sessionId) === client.global.get(\"session_id\"))");
  }

  private void case123() throws Exception {
    var response =
        request(
            "PUT",
            "/api/assignments/{{assignment_id}}/submissions/{{submission_id}}/grade",
            """
                {"version": {{submission_version}},
                  "score": 90,
                  "feedback": "Good explanation"
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("submission_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "score"), 90), "response.body.score === 90");
    verify(eq(field(response.body(), "status"), "GRADED"), "response.body.status === \"GRADED\"");
  }

  private void case124() throws Exception {
    var response =
        request(
            "PUT",
            "/api/lessons/{{lesson_id}}/assignments/{{assignment_id}}",
            """
                {"version": {{assignment_version}},
                  "title": "Java homework 1",
                  "description": "Explain JVM, JDK and JRE",
                  "dueAt": "{{due_at}}",
                  "maxScore": 100
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case125() throws Exception {
    long assignment = scenarioId("assignment_id");
    var before = jdbc.queryForList("SELECT * FROM submissions WHERE assignment_id = ? ORDER BY id", assignment);
    verify(!before.isEmpty(), "Precondition: assignment has submissions");
    var response =
        request(
            "DELETE",
            "/api/lessons/{{lesson_id}}/assignments/{{assignment_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
    checkError(response, 409, "BUSINESS_CONFLICT");
    verify(dbCount("SELECT count(*) FROM assignments WHERE id = ?", assignment) == 1, "Assignment was deleted");
    verify(before.equals(jdbc.queryForList("SELECT * FROM submissions WHERE assignment_id = ? ORDER BY id", assignment)), "Submissions changed after rejected deletion");
  }

  private void case126() throws Exception {
    var response =
        request(
            "POST",
            "/api/modules/{{module_id}}/quiz",
            """
                {
                  "title": "Java quiz",
                  "timeLimit": 20
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("quiz_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    verify(
        eq(response.header("Location"), plus("/api/quizzes/", field(response.body(), "id"))),
        "response.headers.valueOf(\"Location\") === \"/api/quizzes/\" + response.body.id");
    if (eq(response.status(), 201)) {
      set("quiz_id", str(field(response.body(), "id")));
    }
  }

  private void case127() throws Exception {
    var response =
        request(
            "GET",
            "/api/modules/{{module_id}}/quiz",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(str(field(response.body(), "id")), get("quiz_id")),
        "String(response.body.id) === client.global.get(\"quiz_id\")");
  }

  private void case128() throws Exception {
    var response =
        request(
            "PUT",
            "/api/quizzes/{{quiz_id}}",
            """
                {"version": {{quiz_version}},
                  "title": "Java quiz updated",
                  "timeLimit": 30,
                  "maxAttempts": 2
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("quiz_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "maxAttempts"), 2), "response.body.maxAttempts === 2");
  }

  private void case129() throws Exception {
    var response =
        request(
            "GET", "/api/quizzes/{{quiz_id}}", null, "Authorization", "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(str(field(response.body(), "id")), get("quiz_id")),
        "String(response.body.id) === client.global.get(\"quiz_id\")");
  }

  private void case130() throws Exception {
    long module = scenarioId("module_id");
    var before = jdbc.queryForList("SELECT * FROM quizzes WHERE module_id = ? ORDER BY id", module);
    verify(before.size() == 1, "Precondition: one quiz per module");
    var response =
        request(
            "POST",
            "/api/modules/{{module_id}}/quiz",
            """
                {
                  "title": "Duplicate",
                  "timeLimit": 20
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
    checkError(response, 409, "BUSINESS_CONFLICT");
    verify(before.equals(jdbc.queryForList("SELECT * FROM quizzes WHERE module_id = ? ORDER BY id", module)), "Duplicate quiz changed database");
  }

  private void case131() throws Exception {
    var response =
        request(
            "POST",
            "/api/modules/{{temp_module_id}}/quiz",
            """
                {
                  "title": "Temporary quiz",
                  "timeLimit": 20
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    if (eq(response.status(), 201)) {
      set("temp_quiz_id", str(field(response.body(), "id")));
    }
  }

  private void case132() throws Exception {
    var response =
        request(
            "POST",
            "/api/quizzes/{{temp_quiz_id}}/attempts",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case133() throws Exception {
    var response =
        request(
            "POST",
            "/api/quizzes/{{quiz_id}}/questions",
            """
                {
                  "text": "Which type is primitive?",
                  "type": "SINGLE_CHOICE",
                  "position": 1,
                  "options": [
                    {
                      "text": "String",
                      "correct": false
                    },
                    {
                      "text": "int",
                      "correct": true
                    },
                    {
                      "text": "Integer",
                      "correct": false
                    }
                  ]
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    verify(
        eq(
            field(
                filter(field(response.body(), "options"), o -> truth(field(o, "correct"))),
                "length"),
            1),
        "response.body.options.filter(o => o.correct).length === 1");
    if (eq(response.status(), 201)) {
      set("question1_id", str(field(response.body(), "id")));
      set(
          "q1_correct_id",
          str(
              field(
                  find(field(response.body(), "options"), o -> eq(field(o, "text"), "int")),
                  "id")));
      set(
          "q1_wrong_id",
          str(
              field(
                  find(field(response.body(), "options"), o -> eq(field(o, "text"), "String")),
                  "id")));
    }
  }

  private void case134() throws Exception {
    var response =
        request(
            "POST",
            "/api/quizzes/{{quiz_id}}/questions",
            """
                {
                  "text": "Which collections implement List?",
                  "type": "MULTIPLE_CHOICE",
                  "position": 2,
                  "options": [
                    {
                      "text": "ArrayList",
                      "correct": true
                    },
                    {
                      "text": "LinkedList",
                      "correct": true
                    },
                    {
                      "text": "HashSet",
                      "correct": false
                    }
                  ]
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    verify(
        eq(
            field(
                filter(field(response.body(), "options"), o -> truth(field(o, "correct"))),
                "length"),
            2),
        "response.body.options.filter(o => o.correct).length === 2");
    if (eq(response.status(), 201)) {
      set("question2_id", str(field(response.body(), "id")));
      set(
          "q2_correct1_id",
          str(
              field(
                  find(field(response.body(), "options"), o -> eq(field(o, "text"), "ArrayList")),
                  "id")));
      set(
          "q2_correct2_id",
          str(
              field(
                  find(field(response.body(), "options"), o -> eq(field(o, "text"), "LinkedList")),
                  "id")));
    }
  }

  private void case135() throws Exception {
    var response =
        request(
            "POST",
            "/api/quizzes/{{quiz_id}}/questions",
            """
                {
                  "text": "Which type is primitive?",
                  "type": "SINGLE_CHOICE",
                  "position": 1,
                  "options": [
                    {
                      "text": "String",
                      "correct": false
                    },
                    {
                      "text": "int",
                      "correct": true
                    },
                    {
                      "text": "Integer",
                      "correct": false
                    }
                  ]
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case136() throws Exception {
    var response =
        request(
            "POST",
            "/api/quizzes/{{quiz_id}}/questions",
            """
                {
                  "text": "Which type is primitive?",
                  "type": "SINGLE_CHOICE",
                  "position": 3,
                  "options": [
                    {
                      "text": "String",
                      "correct": false
                    },
                    {
                      "text": "int",
                      "correct": true
                    },
                    {
                      "text": "Integer",
                      "correct": false
                    }
                  ]
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("temp_question_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    if (eq(response.status(), 201)) {
      set("temp_question_id", str(field(response.body(), "id")));
    }
  }

  private void case137() throws Exception {
    var response =
        request(
            "PUT",
            "/api/quizzes/{{quiz_id}}/questions/{{temp_question_id}}",
            """
                {"version": {{temp_question_version}},
                  "text": "Temporary updated question",
                  "type": "MULTIPLE_CHOICE",
                  "position": 3,
                  "options": [
                    {
                      "text": "ArrayList",
                      "correct": true
                    },
                    {
                      "text": "LinkedList",
                      "correct": true
                    },
                    {
                      "text": "HashSet",
                      "correct": false
                    }
                  ]
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    if ((((ge(response.status(), 200) && lt(response.status(), 300)) && truth(response.body()))
        && eq(typeOf(field(response.body(), "version")), "number"))) {
      set("temp_question_version", str(field(response.body(), "version")));
    }
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "type"), "MULTIPLE_CHOICE"),
        "response.body.type === \"MULTIPLE_CHOICE\"");
  }

  private void case138() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/quizzes/{{quiz_id}}/questions/{{temp_question_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case139() throws Exception {
    var response =
        request(
            "POST",
            "/api/quizzes/{{temp_quiz_id}}/questions",
            """
                {
                  "text": "Which type is primitive?",
                  "type": "SINGLE_CHOICE",
                  "position": 1,
                  "options": [
                    {
                      "text": "String",
                      "correct": false
                    },
                    {
                      "text": "int",
                      "correct": true
                    },
                    {
                      "text": "Integer",
                      "correct": false
                    }
                  ]
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
  }

  private void case140() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/quizzes/{{temp_quiz_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case141() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/courses/{{course_id}}/modules/{{temp_module_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case142() throws Exception {
    var response =
        request(
            "POST",
            "/api/quizzes/{{quiz_id}}/questions",
            "{\"text\":\"Invalid multiple choice\",\"type\":\"MULTIPLE_CHOICE\",\"position\":3,\"options\":[{\"text\":\"A\",\"correct\":true},{\"text\":\"B\",\"correct\":true}]}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case143() throws Exception {
    var response =
        request(
            "GET",
            "/api/quizzes/{{quiz_id}}/questions/manage",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "length"), 2), "response.body.length === 2");
    verify(
        truth(every(response.body(), q -> eq(field(field(q, "options"), "length"), 3))),
        "Each question has three options");
    verify(
        truth(some(response.body(), q -> eq(str(field(q, "id")), get("question1_id")))),
        "First question is present");
    verify(
        truth(some(response.body(), q -> eq(str(field(q, "id")), get("question2_id")))),
        "Second question is present");
    verify(
        truth(
            every(
                response.body(),
                q ->
                    eq(
                        field(
                            filter(field(q, "options"), o -> eq(field(o, "correct"), true)),
                            "length"),
                        eq(str(field(q, "id")), get("question1_id")) ? 1 : 2))),
        "Correct option counts match question type");
    verify(
        truth(
            every(
                response.body(),
                q ->
                    truth(
                        every(
                            field(q, "options"),
                            o -> eq(typeOf(field(o, "correct")), "boolean"))))),
        "response.body.every(q => q.options.every(o => typeof o.correct === \"boolean\"))");
  }

  private void case144() throws Exception {
    var response =
        request(
            "GET",
            "/api/quizzes/{{quiz_id}}/questions",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case145() throws Exception {
    var response =
        request(
            "GET",
            "/api/quizzes/{{quiz_id}}/questions/manage",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case146() throws Exception {
    var response =
        request(
            "POST",
            "/api/quizzes/{{quiz_id}}/attempts",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    verify(
        eq(field(response.body(), "status"), "IN_PROGRESS"),
        "response.body.status === \"IN_PROGRESS\"");
    verify(eq(field(response.body(), "attemptNumber"), 1), "response.body.attemptNumber === 1");
    verify(
        eq(
            response.header("Location"),
            plus(
                plus(plus("/api/quizzes/", get("quiz_id")), "/attempts/"),
                field(response.body(), "id"))),
        "response.headers.valueOf(\"Location\") === \"/api/quizzes/\" + client.global.get(\"quiz_id\") + \"/attempts/\" + response.body.id");
    if (eq(response.status(), 201)) {
      set("attempt1_id", str(field(response.body(), "id")));
    }
  }

  private void case147() throws Exception {
    var response =
        request(
            "GET",
            "/api/quizzes/{{quiz_id}}/questions",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "length"), 2), "response.body.length === 2");
    verify(
        truth(every(response.body(), q -> eq(field(field(q, "options"), "length"), 3))),
        "Each question has three options");
    verify(
        truth(some(response.body(), q -> eq(str(field(q, "id")), get("question1_id")))),
        "First question is present");
    verify(
        truth(some(response.body(), q -> eq(str(field(q, "id")), get("question2_id")))),
        "Second question is present");
    verify(
        truth(
            every(
                response.body(),
                q -> truth(every(field(q, "options"), o -> !truth(has(o, "correct")))))),
        "response.body.every(q => q.options.every(o => !Object.prototype.hasOwnProperty.call(o, \"correct\")))");
  }

  private void case148() throws Exception {
    var response =
        request(
            "GET",
            "/api/users/me/progress?courseId={{course_id}}",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        truth(
            some(
                field(field(field(response.body(), "content"), 0), "quizzes"),
                q ->
                    ((eq(str(field(q, "quizId")), get("quiz_id"))
                            && eq(field(q, "status"), "IN_PROGRESS"))
                        && eq(field(q, "bestScore"), null)))),
        "response.body.content[0].quizzes.some(q => String(q.quizId) === client.global.get(\"quiz_id\") && q.status === \"IN_PROGRESS\" && q.bestScore === null)");
  }

  private void case149() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/gradebook",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        truth(
            some(
                field(field(field(field(response.body(), "students"), "content"), 0), "results"),
                x ->
                    ((eq(field(x, "type"), "QUIZ") && eq(field(x, "status"), "IN_PROGRESS"))
                        && eq(field(x, "score"), null)))),
        "response.body.students.content[0].results.some(x => x.type === \"QUIZ\" && x.status === \"IN_PROGRESS\" && x.score === null)");
  }

  private void case150() throws Exception {
    set("oversized_answers", oversizedAnswers());
    var response =
        request(
            "POST",
            "/api/quizzes/{{quiz_id}}/attempts/{{attempt1_id}}/submit",
            "{\"answers\": {{oversized_answers}}}",
            "Authorization",
            "Bearer {{student_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case151() throws Exception {
    var response =
        request(
            "POST",
            "/api/quizzes/{{quiz_id}}/attempts",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(str(field(response.body(), "id")), get("attempt1_id")),
        "String(response.body.id) === client.global.get(\"attempt1_id\")");
  }

  private void case152() throws Exception {
    var response =
        request(
            "GET",
            "/api/quizzes/{{quiz_id}}/attempts/{{attempt1_id}}",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "status"), "IN_PROGRESS"),
        "response.body.status === \"IN_PROGRESS\"");
  }

  private void case153() throws Exception {
    var response =
        request(
            "PUT",
            "/api/quizzes/{{quiz_id}}",
            """
                {"version": {{quiz_version}},
                  "title": "Forbidden change",
                  "timeLimit": 20,
                  "maxAttempts": 2
                }
                """,
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case154() throws Exception {
    var response =
        request(
            "POST",
            "/api/quizzes/{{quiz_id}}/attempts/{{attempt1_id}}/submit",
            """
                {
                  "answers": [
                    {
                      "questionId": {{question1_id}},
                      "optionIds": [
                        {{q2_correct1_id}}
                      ]
                    }
                  ]
                }
                """,
            "Authorization",
            "Bearer {{student_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case155() throws Exception {
    var response =
        request(
            "POST",
            "/api/quizzes/{{quiz_id}}/attempts/{{attempt1_id}}/submit",
            """
                {
                  "answers": [
                    {
                      "questionId": {{question1_id}},
                      "optionIds": [
                        {{q1_correct_id}}
                      ]
                    },
                    {
                      "questionId": {{question2_id}},
                      "optionIds": [
                        {{q2_correct1_id}},
                        {{q2_correct2_id}}
                      ]
                    }
                  ]
                }
                """,
            "Authorization",
            "Bearer {{student_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "status"), "SUBMITTED"),
        "response.body.status === \"SUBMITTED\"");
    verify(eq(field(response.body(), "score"), 100), "response.body.score === 100");
  }

  private void case156() throws Exception {
    long attempt = scenarioId("attempt1_id");
    var before = jdbc.queryForMap("SELECT * FROM quiz_submissions WHERE id = ?", attempt);
    var selections = jdbc.queryForList("SELECT * FROM quiz_answer_selections WHERE quiz_submission_id = ? ORDER BY id", attempt);
    var response =
        request(
            "POST",
            "/api/quizzes/{{quiz_id}}/attempts/{{attempt1_id}}/submit",
            """
                {
                  "answers": [
                    {
                      "questionId": {{question1_id}},
                      "optionIds": [
                        {{q1_correct_id}}
                      ]
                    },
                    {
                      "questionId": {{question2_id}},
                      "optionIds": [
                        {{q2_correct1_id}},
                        {{q2_correct2_id}}
                      ]
                    }
                  ]
                }
                """,
            "Authorization",
            "Bearer {{student_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
    checkError(response, 409, "BUSINESS_CONFLICT");
    verify(before.equals(jdbc.queryForMap("SELECT * FROM quiz_submissions WHERE id = ?", attempt)), "Completed attempt changed");
    verify(selections.equals(jdbc.queryForList("SELECT * FROM quiz_answer_selections WHERE quiz_submission_id = ? ORDER BY id", attempt)), "Duplicate attempt changed answers");
  }

  private void case157() throws Exception {
    var response =
        request(
            "POST",
            "/api/quizzes/{{quiz_id}}/attempts",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    verify(eq(field(response.body(), "attemptNumber"), 2), "response.body.attemptNumber === 2");
    if (eq(response.status(), 201)) {
      set("attempt2_id", str(field(response.body(), "id")));
    }
  }

  private void case158() throws Exception {
    var response =
        request(
            "POST",
            "/api/quizzes/{{quiz_id}}/attempts/{{attempt2_id}}/submit",
            """
                {
                  "answers": [
                    {
                      "questionId": {{question1_id}},
                      "optionIds": [
                        {{q1_wrong_id}}
                      ]
                    },
                    {
                      "questionId": {{question2_id}},
                      "optionIds": [
                        {{q2_correct1_id}},
                        {{q2_correct2_id}}
                      ]
                    }
                  ]
                }
                """,
            "Authorization",
            "Bearer {{student_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "score"), 50), "response.body.score === 50");
  }

  private void case159() throws Exception {
    long quiz = scenarioId("quiz_id");
    long student = scenarioId("student_id");
    long before = dbCount("SELECT count(*) FROM quiz_submissions WHERE quiz_id = ? AND student_id = ?", quiz, student);
    var response =
        request(
            "POST",
            "/api/quizzes/{{quiz_id}}/attempts",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
    checkError(response, 409, "BUSINESS_CONFLICT");
    verify(dbCount("SELECT count(*) FROM quiz_submissions WHERE quiz_id = ? AND student_id = ?", quiz, student) == before, "Rejected attempt persisted");
  }

  private void case160() throws Exception {
    var response =
        request(
            "GET",
            "/api/quizzes/{{quiz_id}}/attempts/me?status=SUBMITTED&page=0&size=10",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "totalElements"), 2), "response.body.totalElements === 2");
    verify(
        truth(
            some(
                field(response.body(), "content"),
                x -> eq(str(field(x, "id")), get("attempt1_id")))),
        "response.body.content.some(x => String(x.id) === client.global.get(\"attempt1_id\"))");
    verify(
        truth(
            some(
                field(response.body(), "content"),
                x -> eq(str(field(x, "id")), get("attempt2_id")))),
        "response.body.content.some(x => String(x.id) === client.global.get(\"attempt2_id\"))");
  }

  private void case161() throws Exception {
    var response =
        request(
            "GET",
            "/api/quizzes/{{quiz_id}}/attempts?status=SUBMITTED",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "totalElements"), 2), "response.body.totalElements === 2");
  }

  private void case162() throws Exception {
    var response =
        request(
            "GET",
            "/api/quizzes/{{quiz_id}}/attempts/{{attempt1_id}}/result",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(field(response.body(), "attempt"), "score"), 100),
        "response.body.attempt.score === 100");
    verify(
        eq(field(field(response.body(), "selectedAnswers"), "length"), 3),
        "response.body.selectedAnswers.length === 3");
  }

  private void case163() throws Exception {
    var response =
        request(
            "GET",
            "/api/quizzes/{{quiz_id}}/attempts/{{attempt1_id}}/manage",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(field(response.body(), "attempt"), "score"), 100),
        "response.body.attempt.score === 100");
  }

  private void case164() throws Exception {
    var response =
        request(
            "GET",
            "/api/quizzes/{{quiz_id}}/attempts/{{attempt1_id}}/manage",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case165() throws Exception {
    var response =
        request(
            "GET",
            "/api/quizzes/{{quiz_id}}/attempts/{{attempt1_id}}",
            null,
            "Authorization",
            "Bearer {{other_token}}");
    verify(eq(response.status(), 404), plus("Expected 404, got ", response.status()));
  }

  private void case166() throws Exception {
    var response =
        request(
            "GET",
            "/api/quizzes/{{quiz_id}}/questions",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case167() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/quizzes/{{quiz_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case168() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{course_id}}/reviews",
            """
                {
                  "rating": 5,
                  "comment": "Excellent course"
                }
                """,
            "Authorization",
            "Bearer {{student_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    verify(eq(field(response.body(), "rating"), 5), "response.body.rating === 5");
    verify(
        eq(
            response.header("Location"),
            plus(plus("/api/courses/", get("course_id")), "/reviews/me")),
        "response.headers.valueOf(\"Location\") === \"/api/courses/\" + client.global.get(\"course_id\") + \"/reviews/me\"");
    if (eq(response.status(), 201)) {
      set("review_id", str(field(response.body(), "id")));
    }
  }

  private void case169() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{course_id}}/reviews",
            """
                {
                  "rating": 5,
                  "comment": "Duplicate"
                }
                """,
            "Authorization",
            "Bearer {{student_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case170() throws Exception {
    var response =
        request(
            "PUT",
            "/api/courses/{{course_id}}/reviews/me",
            """
                {
                  "rating": 4,
                  "comment": "Good course"
                }
                """,
            "Authorization",
            "Bearer {{student_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "rating"), 4), "response.body.rating === 4");
  }

  private void case171() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/reviews/me",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(str(field(response.body(), "id")), get("review_id")),
        "String(response.body.id) === client.global.get(\"review_id\")");
  }

  private void case172() throws Exception {
    var response = request("GET", "/api/courses/{{course_id}}/reviews?page=0&size=10", null);
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        truth(
            some(
                field(response.body(), "content"), x -> eq(str(field(x, "id")), get("review_id")))),
        "response.body.content.some(x => String(x.id) === client.global.get(\"review_id\"))");
    verify(
        truth(every(field(response.body(), "content"), x -> !truth(has(x, "studentId")))),
        "response.body.content.every(x => !Object.prototype.hasOwnProperty.call(x, \"studentId\"))");
    verify(eq(field(response.body(), "totalElements"), 1), "response.body.totalElements === 1");
  }

  private void case173() throws Exception {
    var response = request("GET", "/api/courses/{{course_id}}/reviews/rating", null);
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "reviewCount"), 1), "response.body.reviewCount === 1");
    verify(eq(field(response.body(), "averageRating"), 4), "response.body.averageRating === 4");
  }

  private void case174() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/courses/{{course_id}}/reviews/me",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case175() throws Exception {
    var response = request("GET", "/api/courses/{{course_id}}/reviews/rating", null);
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "reviewCount"), 0), "response.body.reviewCount === 0");
    verify(
        eq(field(response.body(), "averageRating"), null), "response.body.averageRating === null");
  }

  private void case176() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{course_id}}/reviews",
            """
                {
                  "rating": 5,
                  "comment": "Regression completed"
                }
                """,
            "Authorization",
            "Bearer {{student_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
  }

  private void case177() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/gradebook?enrollmentStatus=ACTIVE&page=0&size=10",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(field(response.body(), "columns"), "length"), 3),
        "response.body.columns.length === 3");
    verify(
        eq(field(field(response.body(), "students"), "totalElements"), 1),
        "response.body.students.totalElements === 1");
    verify(
        truth(
            some(
                field(field(field(field(response.body(), "students"), "content"), 0), "results"),
                x ->
                    ((eq(field(x, "type"), "ASSIGNMENT") && eq(field(x, "status"), "GRADED"))
                        && eq(field(x, "score"), 90)))),
        "response.body.students.content[0].results.some(x => x.type === \"ASSIGNMENT\" && x.status === \"GRADED\" && x.score === 90)");
    verify(
        truth(
            some(
                field(field(field(field(response.body(), "students"), "content"), 0), "results"),
                x ->
                    ((eq(field(x, "type"), "QUIZ") && eq(field(x, "score"), 100))
                        && eq(field(x, "attemptsUsed"), 2)))),
        "response.body.students.content[0].results.some(x => x.type === \"QUIZ\" && x.score === 100 && x.attemptsUsed === 2)");
    verify(
        truth(
            some(
                field(field(field(field(response.body(), "students"), "content"), 0), "results"),
                x -> eq(field(x, "status"), "NOT_SUBMITTED"))),
        "response.body.students.content[0].results.some(x => x.status === \"NOT_SUBMITTED\")");
  }

  private void case178() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/gradebook?type=ASSIGNMENT&reviewStatus=GRADED&moduleId={{module_id}}",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(field(response.body(), "columns"), "length"), 2),
        "response.body.columns.length === 2");
    verify(
        eq(field(field(response.body(), "students"), "totalElements"), 1),
        "response.body.students.totalElements === 1");
  }

  private void case179() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/gradebook",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case180() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/gradebook",
            null,
            "Authorization",
            "Bearer {{other_token}}");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case181() throws Exception {
    var response =
        request(
            "GET",
            "/api/users/me/progress?courseId={{course_id}}",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "totalElements"), 1), "response.body.totalElements === 1");
    verify(
        eq(field(field(field(response.body(), "content"), 0), "totalAssessments"), 3),
        "response.body.content[0].totalAssessments === 3");
    verify(
        eq(field(field(field(response.body(), "content"), 0), "completedAssessments"), 2),
        "response.body.content[0].completedAssessments === 2");
    verify(
        eq(field(field(field(response.body(), "content"), 0), "progressPercent"), 67),
        "response.body.content[0].progressPercent === 67");
    verify(
        eq(field(field(field(response.body(), "content"), 0), "performancePercent"), 95),
        "response.body.content[0].performancePercent === 95");
    verify(
        eq(
            field(
                field(field(field(response.body(), "content"), 0), "upcomingDeadlines"), "length"),
            1),
        "response.body.content[0].upcomingDeadlines.length === 1");
  }

  private void case182() throws Exception {
    var response =
        request("GET", "/api/dashboard/student", null, "Authorization", "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "activeCourseCount"), 1),
        "response.body.activeCourseCount === 1");
    var course =
        find(
            field(field(response.body(), "courses"), "content"),
            x -> eq(str(field(x, "courseId")), get("course_id")));
    verify(!!truth(course), "!!course");
    verify(
        (eq(field(course, "totalAssessments"), 3) && eq(field(course, "completedAssessments"), 2)),
        "course.totalAssessments === 3 && course.completedAssessments === 2");
    verify(
        (eq(field(course, "progressPercent"), 67) && eq(field(course, "performancePercent"), 95)),
        "course.progressPercent === 67 && course.performancePercent === 95");
    verify(
        (eq(field(course, "assignments"), UNDEFINED) && eq(field(course, "quizzes"), UNDEFINED)),
        "course.assignments === undefined && course.quizzes === undefined");
    var session =
        find(
            field(response.body(), "upcomingSessions"),
            x -> eq(str(field(x, "sessionId")), get("session_id")));
    verify(!!truth(session), "!!session");
    verify(
        eq(str(field(session, "courseId")), get("course_id")),
        "String(session.courseId) === client.global.get(\"course_id\")");
    verify(
        eq(str(field(session, "lessonId")), get("lesson_id")),
        "String(session.lessonId) === client.global.get(\"lesson_id\")");
    verify(
        (eq(field(session, "format"), "ONLINE")
            && eq(typeOf(field(session, "meetingUrl")), "string")),
        "session.format === \"ONLINE\" && typeof session.meetingUrl === \"string\"");
    verify(
        (eq(typeOf(field(session, "startsAt")), "string")
            && eq(typeOf(field(session, "endsAt")), "string")),
        "typeof session.startsAt === \"string\" && typeof session.endsAt === \"string\"");
    set(
        "cf618_student_session",
        json(
            array(
                field(session, "sessionId"),
                field(session, "courseId"),
                field(session, "courseTitle"),
                field(session, "lessonId"),
                field(session, "lessonTitle"),
                field(session, "startsAt"),
                field(session, "endsAt"),
                field(session, "format"),
                field(session, "meetingUrl"))));
    verify(
        truth(
            some(
                field(response.body(), "upcomingDeadlines"),
                x -> eq(str(field(x, "assignmentId")), get("assignment2_id")))),
        "response.body.upcomingDeadlines.some(x => String(x.assignmentId) === client.global.get(\"assignment2_id\"))");
  }

  private void case183() throws Exception {
    var response =
        request("GET", "/api/dashboard/teacher", null, "Authorization", "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "pendingSubmissionCount"), 0),
        "response.body.pendingSubmissionCount === 0");
    var session =
        find(
            field(response.body(), "upcomingSessions"),
            x -> eq(str(field(x, "sessionId")), get("session_id")));
    verify(!!truth(session), "!!session");
    var studentSession = parse(get("cf618_student_session"));
    each(
        array(
            "sessionId",
            "courseId",
            "courseTitle",
            "lessonId",
            "lessonTitle",
            "startsAt",
            "endsAt",
            "format",
            "meetingUrl"),
        (field, index) ->
            verify(
                eq(field(session, field), field(studentSession, index)),
                plus("Session differs: ", field)));
    verify(
        eq(field(field(response.body(), "recentQuizResults"), "length"), 2),
        "response.body.recentQuizResults.length === 2");
    verify(
        truth(
            some(
                field(response.body(), "recentQuizResults"),
                x -> eq(str(field(x, "attemptId")), get("attempt1_id")))),
        "response.body.recentQuizResults.some(x => String(x.attemptId) === client.global.get(\"attempt1_id\"))");
    verify(
        truth(
            some(
                field(response.body(), "recentQuizResults"),
                x -> eq(str(field(x, "attemptId")), get("attempt2_id")))),
        "response.body.recentQuizResults.some(x => String(x.attemptId) === client.global.get(\"attempt2_id\"))");
  }

  private void case184() throws Exception {
    var response =
        request("GET", "/api/dashboard/admin", null, "Authorization", "Bearer {{admin_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(
            field(field(response.body(), "users"), "total"),
            plus(
                plus(
                    field(field(response.body(), "users"), "active"),
                    field(field(response.body(), "users"), "inactive")),
                field(field(response.body(), "users"), "deleted"))),
        "response.body.users.total === response.body.users.active + response.body.users.inactive + response.body.users.deleted");
    verify(
        eq(
            field(field(response.body(), "courses"), "total"),
            plus(
                field(field(response.body(), "courses"), "active"),
                field(field(response.body(), "courses"), "archived"))),
        "response.body.courses.total === response.body.courses.active + response.body.courses.archived");
    verify(
        eq(
            field(field(response.body(), "enrollments"), "total"),
            plus(
                field(field(response.body(), "enrollments"), "active"),
                field(field(response.body(), "enrollments"), "withdrawn"))),
        "response.body.enrollments.total === response.body.enrollments.active + response.body.enrollments.withdrawn");
    verify(
        le(
            field(field(response.body(), "enrollments"), "activeOnActiveCourses"),
            field(field(response.body(), "enrollments"), "active")),
        "response.body.enrollments.activeOnActiveCourses <= response.body.enrollments.active");
    if (eq(response.status(), 200)) {
      set("active_enrollments_before", str(field(field(response.body(), "enrollments"), "active")));
      set("total_enrollments_before", str(field(field(response.body(), "enrollments"), "total")));
    }
  }

  private void case185() throws Exception {
    var response =
        request("GET", "/api/dashboard/admin", null, "Authorization", "Bearer {{student_token}}");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case186() throws Exception {
    var response =
        request("GET", "/api/dashboard/teacher", null, "Authorization", "Bearer {{student_token}}");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case187() throws Exception {
    var response =
        request("GET", "/api/dashboard/student", null, "Authorization", "Bearer {{admin_token}}");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case188() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/courses/{{course_id}}/enrollments/me",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case189() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/modules/{{module_id}}/lessons",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case190() throws Exception {
    var response =
        request(
            "GET",
            "/api/assignments/{{assignment_id}}/submissions/me",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "score"), 90), "response.body.score === 90");
  }

  private void case191() throws Exception {
    var response =
        request(
            "GET",
            "/api/users/me/progress?courseId={{course_id}}",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(field(field(response.body(), "content"), 0), "enrollmentStatus"), "WITHDRAWN"),
        "response.body.content[0].enrollmentStatus === \"WITHDRAWN\"");
    verify(
        eq(field(field(field(response.body(), "content"), 0), "progressPercent"), 67),
        "response.body.content[0].progressPercent === 67");
    verify(
        eq(
            field(
                field(field(field(response.body(), "content"), 0), "upcomingDeadlines"), "length"),
            0),
        "response.body.content[0].upcomingDeadlines.length === 0");
  }

  private void case192() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/gradebook?enrollmentStatus=WITHDRAWN",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(field(response.body(), "students"), "totalElements"), 1),
        "response.body.students.totalElements === 1");
    verify(
        eq(
            field(
                field(field(field(response.body(), "students"), "content"), 0), "enrollmentStatus"),
            "WITHDRAWN"),
        "response.body.students.content[0].enrollmentStatus === \"WITHDRAWN\"");
  }

  private void case193() throws Exception {
    var response =
        request("GET", "/api/dashboard/student", null, "Authorization", "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "activeCourseCount"), 0),
        "response.body.activeCourseCount === 0");
    verify(
        eq(field(field(field(response.body(), "courses"), "content"), "length"), 0),
        "response.body.courses.content.length === 0");
    verify(
        eq(field(field(response.body(), "upcomingSessions"), "length"), 0),
        "response.body.upcomingSessions.length === 0");
    verify(
        eq(field(field(response.body(), "upcomingDeadlines"), "length"), 0),
        "response.body.upcomingDeadlines.length === 0");
  }

  private void case194() throws Exception {
    var response =
        request("GET", "/api/dashboard/admin", null, "Authorization", "Bearer {{admin_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(
            field(field(response.body(), "enrollments"), "active"),
            subtract(number(get("active_enrollments_before")), 1)),
        "response.body.enrollments.active === Number(client.global.get(\"active_enrollments_before\")) - 1");
    verify(
        eq(
            field(field(response.body(), "enrollments"), "total"),
            number(get("total_enrollments_before"))),
        "response.body.enrollments.total === Number(client.global.get(\"total_enrollments_before\"))");
  }

  private void case195() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{course_id}}/enrollments",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(str(field(response.body(), "id")), get("enrollment_id")),
        "String(response.body.id) === client.global.get(\"enrollment_id\")");
    verify(eq(field(response.body(), "status"), "ACTIVE"), "response.body.status === \"ACTIVE\"");
  }

  private void case196() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/modules/{{module_id}}/lessons",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        truth(
            some(
                field(response.body(), "content"), x -> eq(str(field(x, "id")), get("lesson_id")))),
        "response.body.content.some(x => String(x.id) === client.global.get(\"lesson_id\"))");
  }

  private void case197() throws Exception {
    var response =
        request("GET", "/api/dashboard/admin", null, "Authorization", "Bearer {{admin_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(
            field(field(response.body(), "enrollments"), "active"),
            number(get("active_enrollments_before"))),
        "response.body.enrollments.active === Number(client.global.get(\"active_enrollments_before\"))");
  }

  private void case198() throws Exception {
    var response =
        request(
            "PUT",
            "/api/users/{{teacher_id}}/roles",
            "{\"version\": {{teacher_version}},\"roles\":[\"STUDENT\"]}",
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case199() throws Exception {
    var response =
        request(
            "PUT",
            "/api/users/{{student_id}}/roles",
            "{\"version\": {{student_version}},\"roles\":[\"TEACHER\"]}",
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case200() throws Exception {
    var response =
        request(
            "POST",
            "/api/assignments/{{assignment_id}}/submissions",
            "{\"content\":\"Own course\"}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case201() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{course_id}}/enrollments",
            null,
            "Authorization",
            "Bearer {{other_token}}");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    verify(eq(field(response.body(), "status"), "ACTIVE"), "response.body.status === \"ACTIVE\"");
  }

  private void case202() throws Exception {
    var response =
        request(
            "PUT",
            "/api/courses/{{course_id}}/teacher",
            "{\"version\": {{course_version}},\"teacherId\":{{other_id}}}",
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case203() throws Exception {
    var response =
        request(
            "PUT",
            "/api/courses/{{course_id}}",
            """
                {"version": {{course_version}},
                  "title": "Regression updated course {{run_id}}",
                  "description": "Regression course",
                  "categoryId": {{category_id}},
                  "teacherId": {{other_id}},
                  "duration": 56,
                  "startDate": "{{course_start}}"
                }
                """,
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case204() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/courses/{{course_id}}/enrollments/students/{{other_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case205() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/courses/{{course_id}}/enrollments/students/{{other_id}}",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case206() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/courses/{{course_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case207() throws Exception {
    var response =
        request(
            "DELETE", "/api/users/{{other_id}}", null, "Authorization", "Bearer {{admin_token}}");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case208() throws Exception {
    var response =
        request(
            "POST",
            "/api/users/{{other_id}}/deactivate",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "status"), "INACTIVE"), "response.body.status === \"INACTIVE\"");
  }

  private void case209() throws Exception {
    var response = request("GET", "/api/users/me", null, "Authorization", "Bearer {{other_token}}");
    verify(eq(response.status(), 401), plus("Expected 401, got ", response.status()));
  }

  private void case210() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/login",
            """
                {
                  "email": "{{other_updated_email}}",
                  "password": "{{test_password}}"
                }
                """,
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 401), plus("Expected 401, got ", response.status()));
  }

  private void case211() throws Exception {
    var response =
        request(
            "DELETE", "/api/users/{{other_id}}", null, "Authorization", "Bearer {{admin_token}}");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case212() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/courses/{{course_id}}/enrollments/students/{{other_id}}",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case213() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/courses/{{course_id}}/enrollments/students/{{other_id}}",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case214() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/enrollments?status=WITHDRAWN&page=0&size=10",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        truth(
            some(
                field(response.body(), "content"),
                x ->
                    (eq(str(field(x, "studentId")), get("other_id"))
                        && eq(field(x, "status"), "WITHDRAWN")))),
        "response.body.content.some(x => String(x.studentId) === client.global.get(\"other_id\") && x.status === \"WITHDRAWN\")");
  }

  private void case215() throws Exception {
    var response =
        request(
            "POST",
            "/api/users/{{other_id}}/activate",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "status"), "ACTIVE"), "response.body.status === \"ACTIVE\"");
  }

  private void case216() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/login",
            """
                {
                  "email": "{{other_updated_email}}",
                  "password": "{{test_password}}"
                }
                """,
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(typeOf(field(response.body(), "accessToken")), "string"),
        "typeof response.body.accessToken === \"string\"");
    if (eq(response.status(), 200)) {
      set("other_token", str(field(response.body(), "accessToken")));
      set("other_id", str(field(response.body(), "userId")));
    }
  }

  private void case217() throws Exception {
    var response =
        request(
            "POST",
            "/api/users/{{other_id}}/deactivate",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
  }

  private void case218() throws Exception {
    var response =
        request(
            "DELETE", "/api/users/{{other_id}}", null, "Authorization", "Bearer {{admin_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case219() throws Exception {
    var response =
        request("GET", "/api/users/{{other_id}}", null, "Authorization", "Bearer {{admin_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "status"), "DELETED"), "response.body.status === \"DELETED\"");
  }

  private void case220() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/login",
            """
                {
                  "email": "{{other_updated_email}}",
                  "password": "{{test_password}}"
                }
                """,
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 401), plus("Expected 401, got ", response.status()));
  }

  private void case221() throws Exception {
    var response =
        request(
            "POST",
            "/api/users/{{other_id}}/activate",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case222() throws Exception {
    var response =
        request(
            "GET",
            "/api/users/me/progress?courseId={{course_id}}",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(field(field(response.body(), "content"), 0), "progressPercent"), 67),
        "response.body.content[0].progressPercent === 67");
    verify(
        eq(field(field(field(response.body(), "content"), 0), "performancePercent"), 95),
        "response.body.content[0].performancePercent === 95");
  }

  private void case223() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/login",
            "{\"email\":\"reg.unknown.{{run_id}}@courseflow.local\",\"password\":\"{{test_password}}\"}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 401), plus("Expected 401, got ", response.status()));
  }

  private void case224() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/login",
            "{\"email\":\"{{student_email}}\",\"password\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case225() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/login",
            "{\"email\":\"{{student_email}}\",\"password\":\"яяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяя\"}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case226() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/change-password",
            "{\"currentPassword\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"newPassword\":\"{{test_password}}\"}",
            "Authorization",
            "Bearer {{student_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case227() throws Exception {
    var response =
        request(
            "GET",
            "/api/regression-unknown-endpoint",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case228() throws Exception {
    var response =
        request("HEAD", "/api/dashboard/admin", null, "Authorization", "Bearer {{student_token}}");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case229() throws Exception {
    var response =
        request(
            "POST",
            "/api/tags",
            "{\"name\":\"Reg-Case-{{run_id}}\"}",
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    verify(
        eq(field(response.body(), "name"), plus("Reg-Case-", get("run_id"))),
        "response.body.name === \"Reg-Case-\" + client.global.get(\"run_id\")");
    set("tag_id", str(field(response.body(), "id")));
  }

  private void case230() throws Exception {
    var response =
        request(
            "POST",
            "/api/tags",
            "{\"name\":\"reg-case-{{run_id}}\"}",
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case231() throws Exception {
    var response =
        request("DELETE", "/api/tags/{{tag_id}}", null, "Authorization", "Bearer {{admin_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case232() throws Exception {
    var response =
        request(
            "PUT",
            "/api/categories/{{category_id}}",
            "{\"version\": {{category_version}}, \"name\": \"Regression updated {{run_id}}\", \"description\": \"Updated regression category\"}",
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "name"), plus("Regression updated ", get("run_id"))),
        "response.body.name === \"Regression updated \" + client.global.get(\"run_id\")");
  }

  private void case233() throws Exception {
    var response =
        request(
            "PUT",
            "/api/categories/{{category_id}}",
            "{\"name\": \"Forbidden\", \"description\": \"Forbidden\"}",
            "Authorization",
            "Bearer {{student_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case234() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/categories/{{category_id}}",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case235() throws Exception {
    var response =
        request(
            "POST",
            "/api/categories",
            "{\"name\": \"Regression temp {{run_id}}\", \"description\": \"Temporary category\"}",
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    if (eq(response.status(), 201)) {
      set("temp_category_id", str(field(response.body(), "id")));
      set("temp_category_version", str(field(response.body(), "version")));
    }
  }

  private void case236() throws Exception {
    var response =
        request(
            "PUT",
            "/api/categories/{{temp_category_id}}",
            "{\"version\": {{temp_category_version}}, \"name\": \"Regression updated {{run_id}}\", \"description\": \"Duplicate\"}",
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case237() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/categories/{{temp_category_id}}",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case238() throws Exception {
    var response = request("GET", "/api/categories/{{temp_category_id}}", null);
    verify(eq(response.status(), 404), plus("Expected 404, got ", response.status()));
  }

  private void case239() throws Exception {
    var response =
        request("GET", "/api/users/me/profile", null, "Authorization", "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(str(field(response.body(), "userId")), get("student_id")),
        "String(response.body.userId) === client.global.get(\"student_id\")");
    verify(
        (eq(field(response.body(), "bio"), null) && eq(field(response.body(), "avatarUrl"), null)),
        "response.body.bio === null && response.body.avatarUrl === null");
  }

  private void case240() throws Exception {
    var response =
        request(
            "PUT",
            "/api/users/me/profile",
            "{\"bio\": \"Learning Java and Hibernate\", \"avatarUrl\": \"https://example.org/avatar.png\"}",
            "Authorization",
            "Bearer {{student_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "bio"), "Learning Java and Hibernate"),
        "response.body.bio === \"Learning Java and Hibernate\"");
  }

  private void case241() throws Exception {
    var response =
        request("GET", "/api/users/me/profile", null, "Authorization", "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "bio"), "Learning Java and Hibernate"),
        "response.body.bio === \"Learning Java and Hibernate\"");
    verify(
        eq(field(response.body(), "avatarUrl"), "https://example.org/avatar.png"),
        "response.body.avatarUrl === \"https://example.org/avatar.png\"");
  }

  private void case242() throws Exception {
    var response =
        request(
            "PUT",
            "/api/users/me/profile",
            "{\"bio\": \"Invalid avatar\", \"avatarUrl\": \"javascript:alert(1)\"}",
            "Authorization",
            "Bearer {{student_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case243() throws Exception {
    var response =
        request("GET", "/api/users/me/profile", null, "Authorization", "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(str(field(response.body(), "userId")), get("teacher_id")),
        "String(response.body.userId) === client.global.get(\"teacher_id\")");
    verify(eq(field(response.body(), "bio"), null), "response.body.bio === null");
  }

  private void case244() throws Exception {
    var response = request("GET", "/api/users/me/profile", null);
    verify(eq(response.status(), 401), plus("Expected 401, got ", response.status()));
  }

  private void case245() throws Exception {
    var response = request("GET", "/api/teachers/{{teacher_id}}", null);
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(str(field(response.body(), "id")), get("teacher_id")),
        "String(response.body.id) === client.global.get(\"teacher_id\")");
    verify(
        eq(field(response.body(), "displayName"), plus("Regression teacher ", get("run_id"))),
        "response.body.displayName === \"Regression teacher \" + client.global.get(\"run_id\")");
    verify(
        !truth(has(response.body(), "email")),
        "!Object.prototype.hasOwnProperty.call(response.body, \"email\")");
    verify(
        !truth(has(response.body(), "roles")),
        "!Object.prototype.hasOwnProperty.call(response.body, \"roles\")");
  }

  private void case246() throws Exception {
    var response = request("GET", "/api/teachers/{{student_id}}", null);
    verify(eq(response.status(), 404), plus("Expected 404, got ", response.status()));
  }

  private void case247() throws Exception {
    var response =
        request(
            "GET",
            "/api/users/me/assignments?courseId={{course_id}}",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "totalElements"), 2), "response.body.totalElements === 2");
    var graded =
        find(
            field(response.body(), "content"),
            x -> eq(str(field(x, "assignmentId")), get("assignment_id")));
    verify(
        (eq(field(graded, "status"), "GRADED") && eq(field(graded, "score"), 90)),
        "graded.status === \"GRADED\" && graded.score === 90");
    var pending =
        find(
            field(response.body(), "content"),
            x -> eq(str(field(x, "assignmentId")), get("assignment2_id")));
    verify(
        (eq(field(pending, "status"), "NOT_SUBMITTED") && eq(field(pending, "score"), null)),
        "pending.status === \"NOT_SUBMITTED\" && pending.score === null");
  }

  private void case248() throws Exception {
    var response =
        request(
            "GET",
            "/api/users/me/assignments?status=NOT_SUBMITTED&page=0&size=1",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "totalElements"), 1), "response.body.totalElements === 1");
    verify(
        eq(
            str(field(field(field(response.body(), "content"), 0), "assignmentId")),
            get("assignment2_id")),
        "String(response.body.content[0].assignmentId) === client.global.get(\"assignment2_id\")");
  }

  private void case249() throws Exception {
    var response =
        request(
            "GET",
            "/api/users/me/submissions?courseId={{course_id}}&status=GRADED",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "totalElements"), 1), "response.body.totalElements === 1");
    verify(
        eq(
            str(field(field(field(response.body(), "content"), 0), "submissionId")),
            get("submission_id")),
        "String(response.body.content[0].submissionId) === client.global.get(\"submission_id\")");
    verify(
        eq(field(field(field(response.body(), "content"), 0), "score"), 90),
        "response.body.content[0].score === 90");
  }

  private void case250() throws Exception {
    var response =
        request(
            "GET",
            "/api/users/me/quiz-attempts?courseId={{course_id}}&status=SUBMITTED&page=0&size=1",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "totalElements"), 2), "response.body.totalElements === 2");
    verify(
        eq(field(field(response.body(), "content"), "length"), 1),
        "response.body.content.length === 1");
    verify(
        eq(
            str(field(field(field(response.body(), "content"), 0), "attemptId")),
            get("attempt2_id")),
        "String(response.body.content[0].attemptId) === client.global.get(\"attempt2_id\")");
    verify(
        eq(field(field(field(response.body(), "content"), 0), "score"), 50),
        "response.body.content[0].score === 50");
  }

  private void case251() throws Exception {
    var response =
        request(
            "GET",
            "/api/users/me/assignments?size=101",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case252() throws Exception {
    var response =
        request(
            "GET", "/api/users/me/submissions", null, "Authorization", "Bearer {{admin_token}}");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case253() throws Exception {
    var response =
        request(
            "GET",
            "/api/users/me/submissions?courseId={{course_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "totalElements"), 0), "response.body.totalElements === 0");
  }

  private void case254() throws Exception {
    var response =
        request(
            "GET",
            "/api/users/me/quiz-attempts?courseId={{course_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "totalElements"), 0), "response.body.totalElements === 0");
  }

  private void case255() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/courses/{{course_id}}/enrollments/me",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case256() throws Exception {
    var response =
        request(
            "GET",
            "/api/users/me/assignments?courseId={{course_id}}",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "totalElements"), 0), "response.body.totalElements === 0");
  }

  private void case257() throws Exception {
    var response =
        request(
            "GET",
            "/api/users/me/submissions?courseId={{course_id}}",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "totalElements"), 1), "response.body.totalElements === 1");
    verify(
        eq(field(field(field(response.body(), "content"), 0), "score"), 90),
        "response.body.content[0].score === 90");
  }

  private void case258() throws Exception {
    var response =
        request(
            "GET",
            "/api/users/me/quiz-attempts?courseId={{course_id}}",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "totalElements"), 2), "response.body.totalElements === 2");
  }

  private void case259() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{course_id}}/enrollments",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
  }

  private void case260() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses",
            "{\"title\": \"Regression archive {{run_id}}\", \"description\": \"Dashboard archive check\", \"categoryId\": {{category_id}}, \"teacherId\": {{teacher_id}}, \"duration\": 1, \"startDate\": \"{{course_start}}\"}",
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    if (eq(response.status(), 201)) {
      set("temp_course_id", str(field(response.body(), "id")));
    }
  }

  private void case261() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{temp_course_id}}/modules",
            "{\"title\":\"Temporary schedule module\",\"description\":\"Schedule conflict test\",\"position\":1}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    if (eq(response.status(), 201)) {
      set("temp_module_id", str(field(response.body(), "id")));
    }
  }

  private void case262() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{temp_course_id}}/modules/{{temp_module_id}}/lessons",
            "{\"title\":\"Temporary schedule lesson\",\"content\":\"Test\",\"position\":1}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    if (eq(response.status(), 201)) {
      set("temp_lesson_id", str(field(response.body(), "id")));
    }
  }

  private void case263() throws Exception {
    var response =
        request(
            "POST",
            "/api/lessons/{{temp_lesson_id}}/sessions",
            "{\"startsAt\":\"{{session_new_start}}\",\"endsAt\":\"{{session_new_end}}\",\"format\":\"ONLINE\",\"meetingUrl\":\"https://example.org/meeting\"}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case264() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/courses/{{temp_course_id}}/modules/{{temp_module_id}}/lessons/{{temp_lesson_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case265() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/courses/{{temp_course_id}}/modules/{{temp_module_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case266() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{temp_course_id}}/enrollments",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
  }

  private void case267() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{temp_course_id}}/archive",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "status"), "ARCHIVED"), "response.body.status === \"ARCHIVED\"");
  }

  private void case268() throws Exception {
    var response =
        request("GET", "/api/dashboard/student", null, "Authorization", "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "activeCourseCount"), 1),
        "response.body.activeCourseCount === 1");
    verify(
        eq(field(field(response.body(), "courses"), "totalElements"), 1),
        "response.body.courses.totalElements === 1");
    verify(
        eq(
            str(field(field(field(field(response.body(), "courses"), "content"), 0), "courseId")),
            get("course_id")),
        "String(response.body.courses.content[0].courseId) === client.global.get(\"course_id\")");
  }

  private void case269() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/courses/{{temp_course_id}}/enrollments/me",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case270() throws Exception {
    var response = request("GET", "/v3/api-docs", null, "Accept", "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    var doc = response.body();
    verify(
        eq(field(field(doc, "info"), "title"), "CourseFlow REST API"),
        "doc.info.title === \"CourseFlow REST API\"");
    verify(
        eq(
            field(
                field(field(field(doc, "components"), "securitySchemes"), "bearerAuth"), "scheme"),
            "bearer"),
        "doc.components.securitySchemes.bearerAuth.scheme === \"bearer\"");
    verify(
        truth(some(field(doc, "security"), x -> truth(has(x, "bearerAuth")))),
        "doc.security.some(x => Object.prototype.hasOwnProperty.call(x, \"bearerAuth\"))");
    var expected =
        array(
            "delete /api/categories/{id}",
            "delete /api/courses/{courseId}/enrollments/me",
            "delete /api/courses/{courseId}/enrollments/students/{studentId}",
            "delete /api/courses/{courseId}/modules/{moduleId}",
            "delete /api/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}",
            "delete /api/courses/{courseId}/reviews/me",
            "delete /api/courses/{courseId}/tags/{tagId}",
            "delete /api/courses/{id}",
            "delete /api/lessons/{lessonId}/assignments/{assignmentId}",
            "delete /api/lessons/{lessonId}/sessions/{sessionId}",
            "delete /api/quizzes/{quizId}",
            "delete /api/quizzes/{quizId}/questions/{questionId}",
            "delete /api/tags/{id}",
            "delete /api/users/{id}",
            "get /api/assignments/{assignmentId}/submissions",
            "get /api/assignments/{assignmentId}/submissions/me",
            "get /api/categories",
            "get /api/categories/{id}",
            "get /api/courses",
            "get /api/courses/{courseId}/enrollments",
            "get /api/courses/{courseId}/enrollments/me",
            "get /api/courses/{courseId}/gradebook",
            "get /api/courses/{courseId}/modules",
            "get /api/courses/{courseId}/modules/{moduleId}",
            "get /api/courses/{courseId}/modules/{moduleId}/lessons",
            "get /api/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}",
            "get /api/courses/{courseId}/reviews",
            "get /api/courses/{courseId}/reviews/me",
            "get /api/courses/{courseId}/reviews/rating",
            "get /api/courses/{courseId}/tags",
            "get /api/courses/{id}",
            "get /api/dashboard/admin",
            "get /api/dashboard/student",
            "get /api/dashboard/teacher",
            "get /api/lessons/{lessonId}/assignments",
            "get /api/lessons/{lessonId}/assignments/{assignmentId}",
            "get /api/lessons/{lessonId}/sessions",
            "get /api/lessons/{lessonId}/sessions/{sessionId}",
            "get /api/modules/{moduleId}/quiz",
            "get /api/quizzes/{quizId}",
            "get /api/quizzes/{quizId}/attempts",
            "get /api/quizzes/{quizId}/attempts/me",
            "get /api/quizzes/{quizId}/attempts/{attemptId}",
            "get /api/quizzes/{quizId}/attempts/{attemptId}/manage",
            "get /api/quizzes/{quizId}/attempts/{attemptId}/result",
            "get /api/quizzes/{quizId}/questions",
            "get /api/quizzes/{quizId}/questions/manage",
            "get /api/system/status",
            "get /api/tags",
            "get /api/tags/{id}",
            "get /api/teachers/{id}",
            "get /api/users",
            "get /api/users/me",
            "get /api/users/me/assignments",
            "get /api/users/me/enrollments",
            "get /api/users/me/profile",
            "get /api/users/me/progress",
            "get /api/users/me/quiz-attempts",
            "get /api/users/me/submissions",
            "get /api/users/{id}",
            "patch /api/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}/position",
            "patch /api/courses/{courseId}/modules/{moduleId}/position",
            "patch /api/lessons/{lessonId}/assignments/{assignmentId}/deadline",
            "patch /api/lessons/{lessonId}/sessions/{sessionId}/time",
            "post /api/assignments/{assignmentId}/submissions",
            "post /api/auth/change-password",
            "post /api/auth/forgot-password",
            "post /api/auth/login",
            "post /api/auth/logout",
            "post /api/auth/register",
            "post /api/auth/reset-password",
            "post /api/categories",
            "post /api/courses",
            "post /api/courses/{courseId}/enrollments",
            "post /api/courses/{courseId}/modules",
            "post /api/courses/{courseId}/modules/{moduleId}/lessons",
            "post /api/courses/{courseId}/reviews",
            "post /api/courses/{courseId}/tags/{tagId}",
            "post /api/courses/{id}/archive",
            "post /api/lessons/{lessonId}/assignments",
            "post /api/lessons/{lessonId}/sessions",
            "post /api/modules/{moduleId}/quiz",
            "post /api/quizzes/{quizId}/attempts",
            "post /api/quizzes/{quizId}/attempts/{attemptId}/submit",
            "post /api/quizzes/{quizId}/questions",
            "post /api/tags",
            "post /api/users",
            "post /api/users/{id}/activate",
            "post /api/users/{id}/deactivate",
            "post /api/users/{id}/password-reset",
            "put /api/assignments/{assignmentId}/submissions/{submissionId}/grade",
            "put /api/categories/{id}",
            "put /api/courses/{courseId}/modules/{moduleId}",
            "put /api/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}",
            "put /api/courses/{courseId}/reviews/me",
            "put /api/courses/{courseId}/teacher",
            "put /api/courses/{id}",
            "put /api/lessons/{lessonId}/assignments/{assignmentId}",
            "put /api/lessons/{lessonId}/sessions/{sessionId}",
            "put /api/quizzes/{quizId}",
            "put /api/quizzes/{quizId}/questions/{questionId}",
            "put /api/tags/{id}",
            "put /api/users/me/profile",
            "put /api/users/{id}",
            "put /api/users/{id}/roles");
    each(
        expected,
        route -> {
          var parts = split(route, " ");
          var method = field(parts, 0);
          var path = field(parts, 1);
          verify(
              (truth(field(field(doc, "paths"), path))
                  && truth(field(field(field(doc, "paths"), path), method))),
              plus("Missing OpenAPI operation: ", route));
        });
    verify(
        truth(
            isArray(
                field(field(field(field(doc, "paths"), "/api/auth/login"), "post"), "security"))),
        "Array.isArray(doc.paths[\"/api/auth/login\"].post.security)");
    verify(
        eq(
            field(
                field(field(field(field(doc, "paths"), "/api/auth/login"), "post"), "security"),
                "length"),
            0),
        "doc.paths[\"/api/auth/login\"].post.security.length === 0");
    verify(
        eq(
            field(
                field(field(field(field(doc, "paths"), "/api/categories"), "get"), "security"),
                "length"),
            0),
        "doc.paths[\"/api/categories\"].get.security.length === 0");
    var protectedOp = field(field(field(doc, "paths"), "/api/dashboard/admin"), "get");
    verify(
        (eq(field(protectedOp, "security"), UNDEFINED)
            || truth(
                some(field(protectedOp, "security"), x -> !eq(field(x, "bearerAuth"), UNDEFINED)))),
        "protectedOp.security === undefined || protectedOp.security.some(x => x.bearerAuth !== undefined)");
    var publicGet =
        array(
            "/api/categories",
            "/api/categories/{id}",
            "/api/tags",
            "/api/tags/{id}",
            "/api/courses",
            "/api/courses/{id}",
            "/api/courses/{courseId}/tags",
            "/api/courses/{courseId}/modules",
            "/api/courses/{courseId}/modules/{moduleId}",
            "/api/teachers/{id}",
            "/api/courses/{courseId}/reviews",
            "/api/courses/{courseId}/reviews/rating");
    each(
        publicGet,
        path ->
            verify(
                (truth(isArray(field(field(field(field(doc, "paths"), path), "get"), "security")))
                    && eq(
                        field(
                            field(field(field(field(doc, "paths"), path), "get"), "security"),
                            "length"),
                        0)),
                plus("Public GET security: ", path)));
    each(
        array("register", "login", "forgot-password", "reset-password", "change-password"),
        name -> {
          var op = field(field(field(doc, "paths"), plus("/api/auth/", name)), "post");
          verify(!eq(field(field(op, "responses"), "429"), UNDEFINED), plus("Missing 429: ", name));
          verify(
              !eq(
                  field(field(field(field(op, "responses"), "429"), "headers"), "Retry-After"),
                  UNDEFINED),
              plus("Missing Retry-After: ", name));
          if (!eq(name, "change-password")) {
            verify(
                (truth(isArray(field(op, "security")))
                    && eq(field(field(op, "security"), "length"), 0)),
                "Array.isArray(op.security) && op.security.length === 0");
          } else {
            verify(
                (eq(field(op, "security"), UNDEFINED)
                    || truth(
                        some(field(op, "security"), x -> !eq(field(x, "bearerAuth"), UNDEFINED)))),
                "op.security === undefined || op.security.some(x => x.bearerAuth !== undefined)");
          }
        });
    each(
        array(
            "/api/courses/{courseId}/reviews/me",
            "/api/users/me/progress",
            "/api/courses/{courseId}/gradebook"),
        path -> {
          var op = field(field(field(doc, "paths"), path), "get");
          verify(
              (eq(field(op, "security"), UNDEFINED)
                  || truth(
                      some(field(op, "security"), x -> !eq(field(x, "bearerAuth"), UNDEFINED)))),
              plus("Protected GET: ", path));
        });
    verify(
        !eq(
            field(
                field(field(field(field(doc, "paths"), "/api/auth/register"), "post"), "responses"),
                "201"),
            UNDEFINED),
        "doc.paths[\"/api/auth/register\"].post.responses[\"201\"] !== undefined");
    verify(
        !eq(
            field(
                field(field(field(field(doc, "paths"), "/api/auth/logout"), "post"), "responses"),
                "204"),
            UNDEFINED),
        "doc.paths[\"/api/auth/logout\"].post.responses[\"204\"] !== undefined");
    verify(
        (truth(field(field(doc, "components"), "schemas"))
            && !eq(
                field(field(field(doc, "components"), "schemas"), "ApiErrorResponse"), UNDEFINED)),
        "Missing OpenAPI schema: ApiErrorResponse");
  }

  private void case271() throws Exception {
    var response = request("GET", "/swagger-ui/index.html", null, "Accept", "text/html");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        truth(includes(response.header("Content-Type"), "text/html")),
        "response.headers.valueOf(\"Content-Type\").includes(\"text/html\")");
  }

  private void case272() throws Exception {
    var response = request("GET", "/v3/api-docs/swagger-config", null);
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
  }

  private void case273() throws Exception {
    var response =
        request(
            "GET",
            "/api/categories?q=Regression%20updated%20{{run_id}}&page=0&size=5&sortBy=name&direction=asc",
            null);
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        truth(
            some(
                field(response.body(), "content"),
                x -> eq(str(field(x, "id")), get("category_id")))),
        "Updated category not found using canonical q parameter");
  }

  private void case274() throws Exception {
    var response = request("POST", "/api/auth/login", "not-json", "Content-Type", "text/plain");
    verify(eq(response.status(), 415), plus("Expected 415, got ", response.status()));
    verify(
        eq(field(response.body(), "code"), "UNSUPPORTED_MEDIA_TYPE"),
        "response.body.code === \"UNSUPPORTED_MEDIA_TYPE\"");
    verify(
        eq(field(response.body(), "requestId"), response.header("X-Request-Id")),
        "response.body.requestId === response.headers.valueOf(\"X-Request-Id\")");
  }

  private void case275() throws Exception {
    var response =
        request("POST", "/api/auth/login", "{\"email\":", "Content-Type", "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
    verify(
        eq(field(response.body(), "code"), "INVALID_JSON"),
        "response.body.code === \"INVALID_JSON\"");
    verify(
        eq(field(response.body(), "requestId"), response.header("X-Request-Id")),
        "response.body.requestId === response.headers.valueOf(\"X-Request-Id\")");
  }

  private void case276() throws Exception {
    var response =
        request(
            "POST",
            "/api/users",
            "{\"displayName\":\"Temporary password {{run_id}}\",\"email\":\"{{temp_password_email}}\",\"password\":\"{{test_password}}\",\"roles\":[\"STUDENT\"]}",
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), "response.status === 201");
  }

  private void case277() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/login",
            "{\"email\":\"{{temp_password_email}}\",\"password\":\"{{test_password}}\"}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), "response.status === 200");
    verify(
        eq(field(response.body(), "passwordChangeRequired"), true),
        "response.body.passwordChangeRequired === true");
    if (eq(response.status(), 200)) {
      set("temp_password_token", str(field(response.body(), "accessToken")));
    }
  }

  private void case278() throws Exception {
    var response =
        request("GET", "/api/users/me", null, "Authorization", "Bearer {{temp_password_token}}");
    verify(eq(response.status(), 403), "response.status === 403");
  }

  private void case279() throws Exception {
    var response =
        request(
            "GET",
            "/api/dashboard/student",
            null,
            "Authorization",
            "Bearer {{temp_password_token}}");
    verify(eq(response.status(), 403), "response.status === 403");
  }

  private void case280() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/change-password",
            "{\"currentPassword\":\"{{test_password}}\",\"newPassword\":\"{{test_password}}\"}",
            "Authorization",
            "Bearer {{temp_password_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), "response.status === 400");
  }

  private void case281() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/change-password",
            "{\"currentPassword\":\"{{test_password}}\",\"newPassword\":\"{{new_password}}\"}",
            "Authorization",
            "Bearer {{temp_password_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 204), "response.status === 204");
  }

  private void case282() throws Exception {
    var response =
        request("GET", "/api/users/me", null, "Authorization", "Bearer {{temp_password_token}}");
    verify(eq(response.status(), 401), "response.status === 401");
  }

  private void case283() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/login",
            "{\"email\":\"{{temp_password_email}}\",\"password\":\"{{new_password}}\"}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), "response.status === 200");
    verify(
        eq(field(response.body(), "passwordChangeRequired"), false),
        "response.body.passwordChangeRequired === false");
    if (eq(response.status(), 200)) {
      set("temp_password_new_token", str(field(response.body(), "accessToken")));
    }
  }

  private void case284() throws Exception {
    var response =
        request(
            "GET", "/api/users/me", null, "Authorization", "Bearer {{temp_password_new_token}}");
    verify(eq(response.status(), 200), "response.status === 200");
  }

  private void case285() throws Exception {
    var response = request("GET", "/api/courses/{{course_id}}", null);
    verify(eq(response.status(), 200), "response.status === 200");
    verify(
        eq(typeOf(field(response.body(), "version")), "number"),
        "typeof response.body.version === \"number\"");
    if (eq(response.status(), 200)) {
      set("stale_course_version", str(field(response.body(), "version")));
    }
  }

  private void case286() throws Exception {
    var response =
        request(
            "PUT",
            "/api/courses/{{course_id}}",
            "{\"version\":{{stale_course_version}},\"title\":\"Versioned course {{run_id}}\",\"description\":\"Regression course\",\"categoryId\":{{category_id}},\"teacherId\":{{teacher_id}},\"duration\":56,\"startDate\":\"{{course_start}}\"}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), "response.status === 200");
    verify(
        gt(field(response.body(), "version"), number(get("stale_course_version"))),
        "response.body.version > Number(client.global.get(\"stale_course_version\"))");
    verify(
        eq(field(response.body(), "title"), plus("Versioned course ", get("run_id"))),
        "response.body.title === \"Versioned course \" + client.global.get(\"run_id\")");
  }

  private void case287() throws Exception {
    long course = scenarioId("course_id");
    var before = jdbc.queryForMap("SELECT * FROM courses WHERE id = ?", course);
    var response =
        request(
            "PUT",
            "/api/courses/{{course_id}}",
            "{\"version\":{{stale_course_version}},\"title\":\"Stale overwrite {{run_id}}\",\"description\":\"Regression course\",\"categoryId\":{{category_id}},\"teacherId\":{{teacher_id}},\"duration\":56,\"startDate\":\"{{course_start}}\"}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), "response.status === 409");
    verify(
        eq(field(response.body(), "code"), "CONCURRENT_CONFLICT"),
        "response.body.code === \"CONCURRENT_CONFLICT\"");
    checkError(response, 409, "CONCURRENT_CONFLICT");
    verify(before.equals(jdbc.queryForMap("SELECT * FROM courses WHERE id = ?", course)), "Stale update changed course");
  }

  private void case288() throws Exception {
    var response =
        request(
            "PUT",
            "/api/courses/{{course_id}}",
            "{\"title\":\"Missing version {{run_id}}\",\"description\":\"Regression course\",\"categoryId\":{{category_id}},\"teacherId\":{{teacher_id}},\"duration\":56,\"startDate\":\"{{course_start}}\"}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), "response.status === 400");
  }

  private void case289() throws Exception {
    var response = request("GET", "/api/courses/{{course_id}}", null);
    verify(eq(response.status(), 200), "response.status === 200");
    verify(
        eq(field(response.body(), "title"), plus("Versioned course ", get("run_id"))),
        "response.body.title === \"Versioned course \" + client.global.get(\"run_id\")");
  }

  private void case290() throws Exception {
    var response =
        request(
            "POST",
            "/api/categories",
            "{\"name\":\"REGRESSION UPDATED {{run_id}}\",\"description\":\"Case variant\"}",
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), "response.status === 409");
  }

  private void case291() throws Exception {
    var response =
        request(
            "PUT",
            "/api/categories/{{category_id}}",
            "{\"version\":{{category_version}},\"name\":\"Stale category {{run_id}}\",\"description\":\"Stale\"}",
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), "response.status === 409");
    verify(
        eq(field(response.body(), "code"), "CONCURRENT_CONFLICT"),
        "response.body.code === \"CONCURRENT_CONFLICT\"");
  }

  private void case292() throws Exception {
    var response =
        request(
            "PUT",
            "/api/categories/{{category_id}}",
            "{\"name\":\"Missing version {{run_id}}\",\"description\":\"Missing version\"}",
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), "response.status === 400");
  }

  private void case293() throws Exception {
    var response = request("GET", "/api/categories/{{category_id}}", null);
    verify(eq(response.status(), 200), "response.status === 200");
    verify(
        eq(field(response.body(), "name"), plus("Regression updated ", get("run_id"))),
        "response.body.name === \"Regression updated \" + client.global.get(\"run_id\")");
    verify(
        gt(field(response.body(), "version"), number(get("category_version"))),
        "response.body.version > Number(client.global.get(\"category_version\"))");
  }

  private void case294() throws Exception {
    var response =
        request(
            "POST",
            "/api/tags",
            "{\"name\":\"version-{{run_id}}\"}",
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), "response.status === 201");
    verify(
        eq(typeOf(field(response.body(), "version")), "number"),
        "typeof response.body.version === \"number\"");
    if (eq(response.status(), 201)) {
      set("version_test_tag_id", str(field(response.body(), "id")));
      set("version_test_tag_version", str(field(response.body(), "version")));
    }
  }

  private void case295() throws Exception {
    var response =
        request(
            "PUT",
            "/api/tags/{{version_test_tag_id}}",
            "{\"name\":\"version-updated-{{run_id}}\",\"version\":{{version_test_tag_version}}}",
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), "response.status === 200");
    verify(
        gt(field(response.body(), "version"), number(get("version_test_tag_version"))),
        "response.body.version > Number(client.global.get(\"version_test_tag_version\"))");
  }

  private void case296() throws Exception {
    var response =
        request(
            "PUT",
            "/api/tags/{{version_test_tag_id}}",
            "{\"name\":\"version-stale-{{run_id}}\",\"version\":{{version_test_tag_version}}}",
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), "response.status === 409");
    verify(
        eq(field(response.body(), "code"), "CONCURRENT_CONFLICT"),
        "response.body.code === \"CONCURRENT_CONFLICT\"");
  }

  private void case297() throws Exception {
    var response =
        request(
            "PUT",
            "/api/tags/{{version_test_tag_id}}",
            "{\"name\":\"version-missing-{{run_id}}\"}",
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), "response.status === 400");
  }

  private void case298() throws Exception {
    var response = request("GET", "/api/tags/{{version_test_tag_id}}", null);
    verify(eq(response.status(), 200), "response.status === 200");
    verify(
        eq(field(response.body(), "name"), plus("version-updated-", get("run_id"))),
        "response.body.name === \"version-updated-\" + client.global.get(\"run_id\")");
  }

  private void case299() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/tags/{{version_test_tag_id}}",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 204), "response.status === 204");
  }

  private void case300() throws Exception {
    var response =
        request(
            "GET",
            "/api/quizzes/{{quiz_id}}/attempts/me?status=SUBMITTED&sortBy=status&direction=asc&page=0&size=10",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), "response.status === 200");
    verify(
        eq(field(field(response.body(), "content"), "length"), 2),
        "response.body.content.length === 2");
    verify(
        truth(every(field(response.body(), "content"), x -> eq(field(x, "status"), "SUBMITTED"))),
        "response.body.content.every(x => x.status === \"SUBMITTED\")");
    var excluded =
        request(
            "GET",
            "/api/quizzes/{{quiz_id}}/attempts/me?status=IN_PROGRESS&page=0&size=10",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(excluded.status(), 200), "IN_PROGRESS filter accepted");
    verify(
        eq(field(field(excluded.body(), "content"), "length"), 0),
        "Completed attempts excluded by IN_PROGRESS filter");
    verify(eq(field(excluded.body(), "totalElements"), 0), "Filtered total is zero");
  }

  private void case301() throws Exception {
    var response =
        request(
            "GET",
            "/api/quizzes/{{quiz_id}}/attempts/me?sortBy=finishedAt&direction=desc&page=0&size=10",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), "response.status === 200");
    verify(
        eq(field(field(response.body(), "content"), "length"), 2),
        "response.body.content.length === 2");
    verify(
        ge(
            instant(field(field(field(response.body(), "content"), 0), "finishedAt")),
            instant(field(field(field(response.body(), "content"), 1), "finishedAt"))),
        "Date.parse(response.body.content[0].finishedAt) >= Date.parse(response.body.content[1].finishedAt)");
  }

  private void case302() throws Exception {
    var response =
        request(
            "POST",
            "/api/users/{{teacher_id}}/deactivate",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case303() throws Exception {
    var response =
        request(
            "GET", "/api/users/{{teacher_id}}", null, "Authorization", "Bearer {{admin_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "status"), "ACTIVE"), "response.body.status === \"ACTIVE\"");
  }

  private void case304() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{course_id}}/archive",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case305() throws Exception {
    var response =
        request(
            "GET", "/api/courses/{{course_id}}", null, "Authorization", "Bearer {{admin_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "status"), "ACTIVE"), "response.body.status === \"ACTIVE\"");
    if (eq(response.status(), 200)) {
      set("cf614_course_version", str(field(response.body(), "version")));
    }
  }

  private void case306() throws Exception {
    var response =
        request(
            "PUT",
            "/api/courses/{{course_id}}",
            "{\"version\":{{cf614_course_version}},\"title\":\"Invalid teacher change\",\"description\":\"Regression\",\"categoryId\":{{category_id}},\"teacherId\":{{admin_id}},\"duration\":56,\"startDate\":\"{{course_start}}\"}",
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
    verify(
        eq(field(response.body(), "message"), "Use the dedicated teacher reassignment endpoint"),
        "response.body.message === \"Use the dedicated teacher reassignment endpoint\"");
  }

  private void case307() throws Exception {
    var response =
        request(
            "POST",
            "/api/lessons/{{lesson_id}}/sessions",
            "{\"startsAt\":\"2000-01-01T10:00:00Z\",\"endsAt\":\"2000-01-01T11:00:00Z\",\"format\":\"OFFLINE\"}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case308() throws Exception {
    var response =
        request(
            "GET",
            "/api/lessons/{{lesson_id}}/sessions/{{session_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    if (eq(response.status(), 200)) {
      set("cf614_session_version", str(field(response.body(), "version")));
    }
  }

  private void case309() throws Exception {
    var response =
        request(
            "PUT",
            "/api/lessons/{{lesson_id}}/sessions/{{session_id}}",
            "{\"version\":{{cf614_session_version}},\"startsAt\":\"2000-01-01T10:00:00Z\",\"endsAt\":\"2000-01-01T11:00:00Z\",\"format\":\"OFFLINE\"}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case310() throws Exception {
    var response =
        request(
            "PATCH",
            "/api/lessons/{{lesson_id}}/sessions/{{session_id}}/time",
            "{\"version\":{{cf614_session_version}},\"startsAt\":\"2000-01-01T10:00:00Z\",\"endsAt\":\"2000-01-01T11:00:00Z\"}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case311() throws Exception {
    var response =
        request(
            "GET",
            "/api/lessons/{{lesson_id}}/sessions/{{session_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(instant(field(response.body(), "startsAt")), instant(get("session_new_start"))),
        "Date.parse(response.body.startsAt) === Date.parse(client.global.get(\"session_new_start\"))");
    verify(
        eq(str(field(response.body(), "version")), get("cf614_session_version")),
        "String(response.body.version) === client.global.get(\"cf614_session_version\")");
  }

  private void case312() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{course_id}}/modules",
            "{\"title\":\"Move test 3\",\"position\":3}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    if (eq(response.status(), 201)) {
      set("cf614_module3_id", str(field(response.body(), "id")));
      set("cf614_module3_version", str(field(response.body(), "version")));
    }
  }

  private void case313() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{course_id}}/modules",
            "{\"title\":\"Move test 4\",\"position\":4}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    if (eq(response.status(), 201)) {
      set("cf614_module4_id", str(field(response.body(), "id")));
      set("cf614_module4_version", str(field(response.body(), "version")));
    }
  }

  private void case314() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/modules/{{module_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "position"), 2), "response.body.position === 2");
    if (eq(response.status(), 200)) {
      set("cf614_module_version", str(field(response.body(), "version")));
    }
  }

  private void case315() throws Exception {
    var response =
        request(
            "PATCH",
            "/api/courses/{{course_id}}/modules/{{module_id}}/position",
            "{\"position\":4,\"version\":{{cf614_module_version}}}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "position"), 4), "response.body.position === 4");
    if (eq(response.status(), 200)) {
      set("cf614_module_version", str(field(response.body(), "version")));
    }
  }

  private void case316() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/modules?page=0&size=100",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(
            str(
                field(
                    find(field(response.body(), "content"), x -> eq(field(x, "position"), 2)),
                    "id")),
            get("cf614_module3_id")),
        "String(response.body.content.find(x => x.position === 2).id) === client.global.get(\"cf614_module3_id\")");
    verify(
        eq(
            str(
                field(
                    find(field(response.body(), "content"), x -> eq(field(x, "position"), 3)),
                    "id")),
            get("cf614_module4_id")),
        "String(response.body.content.find(x => x.position === 3).id) === client.global.get(\"cf614_module4_id\")");
    verify(
        eq(
            str(
                field(
                    find(field(response.body(), "content"), x -> eq(field(x, "position"), 4)),
                    "id")),
            get("module_id")),
        "String(response.body.content.find(x => x.position === 4).id) === client.global.get(\"module_id\")");
  }

  private void case317() throws Exception {
    var response =
        request(
            "PATCH",
            "/api/courses/{{course_id}}/modules/{{module_id}}/position",
            "{\"position\":2,\"version\":{{cf614_module_version}}}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "position"), 2), "response.body.position === 2");
    if (eq(response.status(), 200)) {
      set("cf614_module_version", str(field(response.body(), "version")));
    }
  }

  private void case318() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/modules?page=0&size=100",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(
            str(
                field(
                    find(field(response.body(), "content"), x -> eq(field(x, "position"), 2)),
                    "id")),
            get("module_id")),
        "String(response.body.content.find(x => x.position === 2).id) === client.global.get(\"module_id\")");
    verify(
        eq(
            str(
                field(
                    find(field(response.body(), "content"), x -> eq(field(x, "position"), 3)),
                    "id")),
            get("cf614_module3_id")),
        "String(response.body.content.find(x => x.position === 3).id) === client.global.get(\"cf614_module3_id\")");
    verify(
        eq(
            str(
                field(
                    find(field(response.body(), "content"), x -> eq(field(x, "position"), 4)),
                    "id")),
            get("cf614_module4_id")),
        "String(response.body.content.find(x => x.position === 4).id) === client.global.get(\"cf614_module4_id\")");
  }

  private void case319() throws Exception {
    var response =
        request(
            "PATCH",
            "/api/courses/{{course_id}}/modules/{{cf614_module3_id}}/position",
            "{\"position\":3,\"version\":{{cf614_module3_version}}}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case320() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/courses/{{course_id}}/modules/{{cf614_module3_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case321() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/courses/{{course_id}}/modules/{{cf614_module4_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case322() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{course_id}}/modules/{{module_id}}/lessons",
            "{\"title\":\"Move test 3\",\"position\":3,\"content\":\"Move test\"}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    if (eq(response.status(), 201)) {
      set("cf614_lesson3_id", str(field(response.body(), "id")));
      set("cf614_lesson3_version", str(field(response.body(), "version")));
    }
  }

  private void case323() throws Exception {
    var response =
        request(
            "POST",
            "/api/courses/{{course_id}}/modules/{{module_id}}/lessons",
            "{\"title\":\"Move test 4\",\"position\":4,\"content\":\"Move test\"}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    if (eq(response.status(), 201)) {
      set("cf614_lesson4_id", str(field(response.body(), "id")));
      set("cf614_lesson4_version", str(field(response.body(), "version")));
    }
  }

  private void case324() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{lesson_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "position"), 2), "response.body.position === 2");
    if (eq(response.status(), 200)) {
      set("cf614_lesson_version", str(field(response.body(), "version")));
    }
  }

  private void case325() throws Exception {
    var response =
        request(
            "PATCH",
            "/api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{lesson_id}}/position",
            "{\"position\":4,\"version\":{{cf614_lesson_version}}}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "position"), 4), "response.body.position === 4");
    if (eq(response.status(), 200)) {
      set("cf614_lesson_version", str(field(response.body(), "version")));
    }
  }

  private void case326() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/modules/{{module_id}}/lessons?page=0&size=100",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(
            str(
                field(
                    find(field(response.body(), "content"), x -> eq(field(x, "position"), 2)),
                    "id")),
            get("cf614_lesson3_id")),
        "String(response.body.content.find(x => x.position === 2).id) === client.global.get(\"cf614_lesson3_id\")");
    verify(
        eq(
            str(
                field(
                    find(field(response.body(), "content"), x -> eq(field(x, "position"), 3)),
                    "id")),
            get("cf614_lesson4_id")),
        "String(response.body.content.find(x => x.position === 3).id) === client.global.get(\"cf614_lesson4_id\")");
    verify(
        eq(
            str(
                field(
                    find(field(response.body(), "content"), x -> eq(field(x, "position"), 4)),
                    "id")),
            get("lesson_id")),
        "String(response.body.content.find(x => x.position === 4).id) === client.global.get(\"lesson_id\")");
  }

  private void case327() throws Exception {
    var response =
        request(
            "PATCH",
            "/api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{lesson_id}}/position",
            "{\"position\":2,\"version\":{{cf614_lesson_version}}}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "position"), 2), "response.body.position === 2");
    if (eq(response.status(), 200)) {
      set("cf614_lesson_version", str(field(response.body(), "version")));
    }
  }

  private void case328() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/modules/{{module_id}}/lessons?page=0&size=100",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(
            str(
                field(
                    find(field(response.body(), "content"), x -> eq(field(x, "position"), 2)),
                    "id")),
            get("lesson_id")),
        "String(response.body.content.find(x => x.position === 2).id) === client.global.get(\"lesson_id\")");
    verify(
        eq(
            str(
                field(
                    find(field(response.body(), "content"), x -> eq(field(x, "position"), 3)),
                    "id")),
            get("cf614_lesson3_id")),
        "String(response.body.content.find(x => x.position === 3).id) === client.global.get(\"cf614_lesson3_id\")");
    verify(
        eq(
            str(
                field(
                    find(field(response.body(), "content"), x -> eq(field(x, "position"), 4)),
                    "id")),
            get("cf614_lesson4_id")),
        "String(response.body.content.find(x => x.position === 4).id) === client.global.get(\"cf614_lesson4_id\")");
  }

  private void case329() throws Exception {
    var response =
        request(
            "PATCH",
            "/api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{cf614_lesson3_id}}/position",
            "{\"position\":3,\"version\":{{cf614_lesson3_version}}}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case330() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{cf614_lesson3_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case331() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{cf614_lesson4_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case332() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/gradebook?columnPage=0&columnSize=1",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    var b = response.body();
    verify(
        (((eq(field(b, "columnPage"), 0) && eq(field(b, "columnSize"), 1))
                && eq(field(b, "totalColumns"), 3))
            && eq(field(b, "totalColumnPages"), 3)),
        "b.columnPage === 0 && b.columnSize === 1 && b.totalColumns === 3 && b.totalColumnPages === 3");
    verify(
        (eq(field(field(b, "columns"), "length"), 1)
            && eq(field(field(field(b, "columns"), 0), "type"), "ASSIGNMENT")),
        "b.columns.length === 1 && b.columns[0].type === \"ASSIGNMENT\"");
    verify(
        gt(field(field(field(b, "students"), "content"), "length"), 0),
        "b.students.content.length > 0");
    verify(
        truth(
            every(
                field(field(b, "students"), "content"),
                x ->
                    (eq(field(field(x, "results"), "length"), 1)
                        && eq(
                            field(field(field(x, "results"), 0), "assessmentId"),
                            field(field(field(b, "columns"), 0), "id"))))),
        "b.students.content.every(x => x.results.length === 1 && x.results[0].assessmentId === b.columns[0].id)");
    set("cf615_first_column", str(field(field(field(b, "columns"), 0), "id")));
  }

  private void case333() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/gradebook?columnPage=1&columnSize=1",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    var b = response.body();
    verify(
        (eq(field(b, "columnPage"), 1) && eq(field(b, "totalColumns"), 3)),
        "b.columnPage === 1 && b.totalColumns === 3");
    verify(
        (eq(field(field(b, "columns"), "length"), 1)
            && eq(field(field(field(b, "columns"), 0), "type"), "ASSIGNMENT")),
        "b.columns.length === 1 && b.columns[0].type === \"ASSIGNMENT\"");
    verify(
        !eq(str(field(field(field(b, "columns"), 0), "id")), get("cf615_first_column")),
        "String(b.columns[0].id) !== client.global.get(\"cf615_first_column\")");
    verify(
        truth(
            some(
                field(field(b, "students"), "content"),
                x -> eq(str(field(x, "studentId")), get("student_id")))),
        "Known student must remain in gradebook");
    verify(
        truth(
            every(
                field(field(b, "students"), "content"),
                x ->
                    (eq(field(field(x, "results"), "length"), 1)
                        && eq(
                            field(field(field(x, "results"), 0), "assessmentId"),
                            field(field(field(b, "columns"), 0), "id"))))),
        "b.students.content.every(x => x.results.length === 1 && x.results[0].assessmentId === b.columns[0].id)");
  }

  private void case334() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/gradebook?columnPage=2&columnSize=1",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    var b = response.body();
    verify(
        (eq(field(field(b, "columns"), "length"), 1)
            && eq(field(field(field(b, "columns"), 0), "type"), "QUIZ")),
        "b.columns.length === 1 && b.columns[0].type === \"QUIZ\"");
    verify(
        eq(str(field(field(field(b, "columns"), 0), "id")), get("quiz_id")),
        "String(b.columns[0].id) === client.global.get(\"quiz_id\")");
    verify(
        truth(
            some(
                field(field(b, "students"), "content"),
                x ->
                    (eq(str(field(x, "studentId")), get("student_id"))
                        && eq(field(field(field(x, "results"), 0), "score"), 100)))),
        "b.students.content.some(x => String(x.studentId) === client.global.get(\"student_id\") && x.results[0].score === 100)");
  }

  private void case335() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/gradebook?columnPage=3&columnSize=1",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        (eq(field(response.body(), "totalColumns"), 3)
            && eq(field(field(response.body(), "columns"), "length"), 0)),
        "response.body.totalColumns === 3 && response.body.columns.length === 0");
    verify(
        truth(
            some(
                field(field(response.body(), "students"), "content"),
                x -> eq(str(field(x, "studentId")), get("student_id")))),
        "Known student must remain in gradebook");
    verify(
        truth(
            every(
                field(field(response.body(), "students"), "content"),
                x -> eq(field(field(x, "results"), "length"), 0))),
        "response.body.students.content.every(x => x.results.length === 0)");
  }

  private void case336() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/gradebook?type=QUIZ&columnSize=1",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        (eq(field(response.body(), "totalColumns"), 1)
            && eq(field(response.body(), "totalColumnPages"), 1)),
        "response.body.totalColumns === 1 && response.body.totalColumnPages === 1");
    verify(
        (eq(field(field(response.body(), "columns"), "length"), 1)
            && eq(field(field(field(response.body(), "columns"), 0), "type"), "QUIZ")),
        "response.body.columns.length === 1 && response.body.columns[0].type === \"QUIZ\"");
  }

  private void case337() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/gradebook?columnSize=101",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case338() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/gradebook?columnPage=-1",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case339() throws Exception {
    var response =
        request("GET", "/api/users/me/progress", null, "Authorization", "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    var c =
        find(
            field(response.body(), "content"),
            x -> eq(str(field(x, "courseId")), get("course_id")));
    verify(
        (!!truth(c) && eq(field(c, "detailsIncluded"), false)),
        "!!c && c.detailsIncluded === false");
    verify(
        (((eq(field(c, "totalAssessments"), 3) && eq(field(c, "completedAssessments"), 2))
                && eq(field(c, "progressPercent"), 67))
            && eq(field(c, "performancePercent"), 95)),
        "c.totalAssessments === 3 && c.completedAssessments === 2 && c.progressPercent === 67 && c.performancePercent === 95");
    verify(
        ((eq(field(c, "totalAssignments"), 2) && eq(field(c, "totalQuizzes"), 1))
            && eq(field(c, "totalUpcomingDeadlines"), 1)),
        "c.totalAssignments === 2 && c.totalQuizzes === 1 && c.totalUpcomingDeadlines === 1");
    verify(
        ((eq(field(field(c, "assignments"), "length"), 0)
                && eq(field(field(c, "quizzes"), "length"), 0))
            && eq(field(field(c, "upcomingDeadlines"), "length"), 0)),
        "c.assignments.length === 0 && c.quizzes.length === 0 && c.upcomingDeadlines.length === 0");
  }

  private void case340() throws Exception {
    var response =
        request(
            "GET",
            "/api/users/me/progress?courseId={{course_id}}&detailPage=0&detailSize=1",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    var c = field(field(response.body(), "content"), 0);
    verify(
        ((eq(field(c, "detailsIncluded"), true) && eq(field(c, "detailPage"), 0))
            && eq(field(c, "detailSize"), 1)),
        "c.detailsIncluded === true && c.detailPage === 0 && c.detailSize === 1");
    verify(
        ((eq(field(c, "totalAssignments"), 2) && eq(field(c, "totalQuizzes"), 1))
            && eq(field(c, "totalUpcomingDeadlines"), 1)),
        "c.totalAssignments === 2 && c.totalQuizzes === 1 && c.totalUpcomingDeadlines === 1");
    verify(
        ((eq(field(field(c, "assignments"), "length"), 1)
                && eq(field(field(c, "quizzes"), "length"), 1))
            && eq(field(field(c, "upcomingDeadlines"), "length"), 1)),
        "c.assignments.length === 1 && c.quizzes.length === 1 && c.upcomingDeadlines.length === 1");
    verify(
        (((eq(field(c, "totalAssessments"), 3) && eq(field(c, "completedAssessments"), 2))
                && eq(field(c, "progressPercent"), 67))
            && eq(field(c, "performancePercent"), 95)),
        "c.totalAssessments === 3 && c.completedAssessments === 2 && c.progressPercent === 67 && c.performancePercent === 95");
    set("cf615_first_assignment", str(field(field(field(c, "assignments"), 0), "assignmentId")));
  }

  private void case341() throws Exception {
    var response =
        request(
            "GET",
            "/api/users/me/progress?courseId={{course_id}}&detailPage=1&detailSize=1",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    var c = field(field(response.body(), "content"), 0);
    verify(
        (((eq(field(c, "detailPage"), 1) && eq(field(field(c, "assignments"), "length"), 1))
                && eq(field(field(c, "quizzes"), "length"), 0))
            && eq(field(field(c, "upcomingDeadlines"), "length"), 0)),
        "c.detailPage === 1 && c.assignments.length === 1 && c.quizzes.length === 0 && c.upcomingDeadlines.length === 0");
    verify(
        !eq(
            str(field(field(field(c, "assignments"), 0), "assignmentId")),
            get("cf615_first_assignment")),
        "String(c.assignments[0].assignmentId) !== client.global.get(\"cf615_first_assignment\")");
    verify(
        (((eq(field(c, "totalAssessments"), 3) && eq(field(c, "completedAssessments"), 2))
                && eq(field(c, "progressPercent"), 67))
            && eq(field(c, "performancePercent"), 95)),
        "c.totalAssessments === 3 && c.completedAssessments === 2 && c.progressPercent === 67 && c.performancePercent === 95");
  }

  private void case342() throws Exception {
    var response =
        request(
            "GET",
            "/api/users/me/progress?courseId={{course_id}}&detailPage=2&detailSize=1",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    var c = field(field(response.body(), "content"), 0);
    verify(
        ((eq(field(field(c, "assignments"), "length"), 0)
                && eq(field(field(c, "quizzes"), "length"), 0))
            && eq(field(field(c, "upcomingDeadlines"), "length"), 0)),
        "c.assignments.length === 0 && c.quizzes.length === 0 && c.upcomingDeadlines.length === 0");
    verify(
        ((eq(field(c, "totalAssessments"), 3) && eq(field(c, "progressPercent"), 67))
            && eq(field(c, "performancePercent"), 95)),
        "c.totalAssessments === 3 && c.progressPercent === 67 && c.performancePercent === 95");
  }

  private void case343() throws Exception {
    var response =
        request(
            "GET",
            "/api/users/me/progress?courseId={{course_id}}&includeDetails=false",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    var c = field(field(response.body(), "content"), 0);
    verify(
        ((eq(field(c, "detailsIncluded"), false) && eq(field(field(c, "assignments"), "length"), 0))
            && eq(field(field(c, "quizzes"), "length"), 0)),
        "c.detailsIncluded === false && c.assignments.length === 0 && c.quizzes.length === 0");
    verify(
        ((eq(field(c, "totalAssessments"), 3) && eq(field(c, "progressPercent"), 67))
            && eq(field(c, "performancePercent"), 95)),
        "c.totalAssessments === 3 && c.progressPercent === 67 && c.performancePercent === 95");
  }

  private void case344() throws Exception {
    var response =
        request(
            "GET",
            "/api/users/me/progress?includeDetails=true",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case345() throws Exception {
    var response =
        request(
            "GET",
            "/api/users/me/progress?courseId={{course_id}}&detailSize=101",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case346() throws Exception {
    var response =
        request(
            "GET",
            "/api/users/me/progress?courseId={{course_id}}&detailPage=-1",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case347() throws Exception {
    var response =
        request(
            "GET",
            "/api/users/me/progress?courseId={{course_id}}&detailPage=2147483647&detailSize=100",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case348() throws Exception {
    var response = request("GET", "/actuator/health/liveness", null);
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "status"), "UP"), "response.body.status === \"UP\"");
    verify(
        looseEq(field(response.body(), "components"), null), "Probe must not disclose components");
    verify(looseEq(field(response.body(), "details"), null), "Probe must not disclose details");
  }

  private void case349() throws Exception {
    var response = request("GET", "/actuator/health/readiness", null);
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "status"), "UP"), "response.body.status === \"UP\"");
    verify(
        looseEq(field(response.body(), "components"), null), "Probe must not disclose components");
    verify(looseEq(field(response.body(), "details"), null), "Probe must not disclose details");
  }

  private void case350() throws Exception {
    var response = request("GET", "/actuator/health/db", null);
    verify(eq(response.status(), 401), plus("Expected 401, got ", response.status()));
  }

  private void case351() throws Exception {
    var response = request("GET", "/actuator/metrics", null);
    verify(eq(response.status(), 401), plus("Expected 401, got ", response.status()));
  }

  private void case352() throws Exception {
    var response =
        request("GET", "/actuator/metrics", null, "Authorization", "Bearer {{student_token}}");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case353() throws Exception {
    var response = request("GET", "/actuator/metrics/courseflow.auth.rate_limited", null);
    verify(eq(response.status(), 401), plus("Expected 401, got ", response.status()));
  }

  private void case354() throws Exception {
    var response =
        request("GET", "/actuator/metrics", null, "Authorization", "Bearer {{admin_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    var expected =
        array(
            "courseflow.auth.rate_limited",
            "courseflow.password.reset.requests",
            "courseflow.password.reset.accepted",
            "courseflow.password.reset.rejected",
            "courseflow.password.reset.processing_failed",
            "courseflow.password.reset.mail_sent",
            "courseflow.password.reset.mail_failed");
    verify(truth(isArray(field(response.body(), "names"))), "Array.isArray(response.body.names)");
    each(
        expected,
        name ->
            verify(ge(indexOf(field(response.body(), "names"), name), 0), plus("Missing ", name)));
  }

  private void case355() throws Exception {
    var response =
        request(
            "GET",
            "/actuator/metrics/courseflow.password.reset.requests",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "name"), "courseflow.password.reset.requests"),
        "response.body.name === \"courseflow.password.reset.requests\"");
    var count =
        find(field(response.body(), "measurements"), m -> eq(field(m, "statistic"), "COUNT"));
    verify(
        ((truth(count) && eq(typeOf(field(count, "value")), "number"))
            && ge(field(count, "value"), 1)),
        "Invalid counter");
    verify(
        (truth(isArray(field(response.body(), "availableTags")))
            && eq(field(field(response.body(), "availableTags"), "length"), 0)),
        "Custom metric must have no identifying tags");
  }

  private void case356() throws Exception {
    var response =
        request(
            "GET",
            "/actuator/metrics/courseflow.password.reset.accepted",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "name"), "courseflow.password.reset.accepted"),
        "response.body.name === \"courseflow.password.reset.accepted\"");
    var count =
        find(field(response.body(), "measurements"), m -> eq(field(m, "statistic"), "COUNT"));
    verify(
        ((truth(count) && eq(typeOf(field(count, "value")), "number"))
            && ge(field(count, "value"), 1)),
        "Invalid counter");
    verify(
        (truth(isArray(field(response.body(), "availableTags")))
            && eq(field(field(response.body(), "availableTags"), "length"), 0)),
        "Custom metric must have no identifying tags");
  }

  private void case357() throws Exception {
    var response =
        request(
            "GET",
            "/actuator/metrics/courseflow.auth.rate_limited",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "name"), "courseflow.auth.rate_limited"),
        "response.body.name === \"courseflow.auth.rate_limited\"");
    var count =
        find(field(response.body(), "measurements"), m -> eq(field(m, "statistic"), "COUNT"));
    verify(
        ((truth(count) && eq(typeOf(field(count, "value")), "number"))
            && ge(field(count, "value"), 0)),
        "Invalid counter");
    verify(
        (truth(isArray(field(response.body(), "availableTags")))
            && eq(field(field(response.body(), "availableTags"), "length"), 0)),
        "Custom metric must have no identifying tags");
  }

  private void case358() throws Exception {
    var response = request("GET", "/actuator/env", null, "Authorization", "Bearer {{admin_token}}");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case359() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/register",
            "{\"displayName\":\"UTF8 test\",\"email\":\"reg.utf8.{{run_id}}@courseflow.local\",\"password\":\"яяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяя\"}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
    verify(
        eq(field(response.body(), "code"), "BAD_REQUEST"),
        "response.body.code === \"BAD_REQUEST\"");
    verify(
        eq(field(response.body(), "message"), "Password must not exceed 72 UTF-8 bytes"),
        "response.body.message === \"Password must not exceed 72 UTF-8 bytes\"");
  }

  private void case360() throws Exception {
    var response =
        request(
            "POST",
            "/api/users",
            "{\"displayName\":\"UTF8 test\",\"email\":\"reg.utf8.{{run_id}}@courseflow.local\",\"password\":\"яяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяя\",\"roles\":[\"STUDENT\"]}",
            "Content-Type",
            "application/json",
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
    verify(
        eq(field(response.body(), "code"), "BAD_REQUEST"),
        "response.body.code === \"BAD_REQUEST\"");
    verify(
        eq(field(response.body(), "message"), "Password must not exceed 72 UTF-8 bytes"),
        "response.body.message === \"Password must not exceed 72 UTF-8 bytes\"");
  }

  private void case361() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/login",
            "{\"email\":\"reg.utf8.{{run_id}}@courseflow.local\",\"password\":\"яяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяя\"}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
    verify(
        eq(field(response.body(), "code"), "BAD_REQUEST"),
        "response.body.code === \"BAD_REQUEST\"");
    verify(
        eq(field(response.body(), "message"), "Password must not exceed 72 UTF-8 bytes"),
        "response.body.message === \"Password must not exceed 72 UTF-8 bytes\"");
  }

  private void case362() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/change-password",
            "{\"currentPassword\":\"яяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяя\",\"newPassword\":\"CourseFlowTest2026!\"}",
            "Content-Type",
            "application/json",
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
    verify(
        eq(field(response.body(), "code"), "BAD_REQUEST"),
        "response.body.code === \"BAD_REQUEST\"");
    verify(
        eq(field(response.body(), "message"), "Current password must not exceed 72 UTF-8 bytes"),
        "response.body.message === \"Current password must not exceed 72 UTF-8 bytes\"");
  }

  private void case363() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/change-password",
            "{\"currentPassword\":\"CourseFlowTest2026!\",\"newPassword\":\"яяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяя\"}",
            "Content-Type",
            "application/json",
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
    verify(
        eq(field(response.body(), "code"), "BAD_REQUEST"),
        "response.body.code === \"BAD_REQUEST\"");
    verify(
        eq(field(response.body(), "message"), "New password must not exceed 72 UTF-8 bytes"),
        "response.body.message === \"New password must not exceed 72 UTF-8 bytes\"");
  }

  private void case364() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/reset-password",
            "{\"token\":\"invalid-{{run_id}}\",\"newPassword\":\"яяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяя\"}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
    verify(
        eq(field(response.body(), "code"), "BAD_REQUEST"),
        "response.body.code === \"BAD_REQUEST\"");
    verify(
        eq(field(response.body(), "message"), "New password must not exceed 72 UTF-8 bytes"),
        "response.body.message === \"New password must not exceed 72 UTF-8 bytes\"");
  }

  private void case365() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/register",
            "{\"displayName\":\" UTF8 test \",\"email\":\"REG.UTF8.{{run_id}}@COURSEFLOW.LOCAL\",\"password\":\"яяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяя\"}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    verify(
        eq(
            field(response.body(), "email"),
            plus(plus("reg.utf8.", get("run_id")), "@courseflow.local")),
        "response.body.email === (\"reg.utf8.\" + client.global.get(\"run_id\") + \"@courseflow.local\")");
    verify(
        eq(field(response.body(), "displayName"), "UTF8 test"),
        "response.body.displayName === \"UTF8 test\"");
    verify(
        (eq(field(field(response.body(), "roles"), "length"), 1)
            && eq(field(field(response.body(), "roles"), 0), "STUDENT")),
        "response.body.roles.length === 1 && response.body.roles[0] === \"STUDENT\"");
    set("cf618_user_id", str(field(response.body(), "id")));
  }

  private void case366() throws Exception {
    var response =
        request(
            "POST",
            "/api/users",
            "{\"displayName\":\"Duplicate UTF8\",\"email\":\"reg.utf8.{{run_id}}@courseflow.local\",\"password\":\"CourseFlowTest2026!\",\"roles\":[\"STUDENT\"]}",
            "Content-Type",
            "application/json",
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
    verify(
        eq(field(response.body(), "code"), "BUSINESS_CONFLICT"),
        "response.body.code === \"BUSINESS_CONFLICT\"");
  }

  private void case367() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/login",
            "{\"email\":\"reg.utf8.{{run_id}}@courseflow.local\",\"password\":\"яяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяя\"}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(typeOf(field(response.body(), "accessToken")), "string"),
        "typeof response.body.accessToken === \"string\"");
    verify(
        eq(field(response.body(), "passwordChangeRequired"), false),
        "response.body.passwordChangeRequired === false");
  }

  private void case368() throws Exception {
    var response =
        request(
            "POST",
            "/api/users/{{cf618_user_id}}/deactivate",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "status"), "INACTIVE"), "response.body.status === \"INACTIVE\"");
  }

  private void case369() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/users/{{cf618_user_id}}",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }

  private void case370() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/gradebook?type=ASSIGNMENT&reviewStatus=NOT_SUBMITTED&enrollmentStatus=ACTIVE&moduleId={{module_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    var row =
        find(
            field(field(response.body(), "students"), "content"),
            x -> eq(str(field(x, "studentId")), get("student_id")));
    verify(!looseEq(row, null), "Student with an unsubmitted assignment must match");
    verify(
        truth(
            some(
                field(row, "results"),
                x ->
                    (eq(field(x, "type"), "ASSIGNMENT")
                        && eq(field(x, "status"), "NOT_SUBMITTED")))),
        "row.results.some(x => x.type === \"ASSIGNMENT\" && x.status === \"NOT_SUBMITTED\")");
  }

  private void case371() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/gradebook?type=ASSIGNMENT&reviewStatus=OVERDUE&moduleId={{module_id}}",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(field(response.body(), "students"), "totalElements"), 0),
        "response.body.students.totalElements === 0");
    verify(
        eq(field(field(field(response.body(), "students"), "content"), "length"), 0),
        "response.body.students.content.length === 0");
    verify(eq(field(response.body(), "totalColumns"), 2), "response.body.totalColumns === 2");
  }

  private void case372() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/gradebook?reviewStatus=OVERDUE",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(field(response.body(), "students"), "totalElements"), 0),
        "response.body.students.totalElements === 0");
    verify(eq(field(response.body(), "totalColumns"), 3), "response.body.totalColumns === 3");
  }

  private void case373() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/gradebook?type=QUIZ&reviewStatus=OVERDUE",
            null,
            "Authorization",
            "Bearer {{teacher_token}}");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
    verify(
        eq(field(response.body(), "code"), "BAD_REQUEST"),
        "response.body.code === \"BAD_REQUEST\"");
  }

  private void case374() throws Exception {
    var response =
        request(
            "GET",
            "/api/courses/{{course_id}}/gradebook?reviewStatus=OVERDUE",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case375() throws Exception {
    var response =
        request(
            "PATCH",
            "/api/lessons/{{lesson_id}}/assignments/{{assignment_id}}/deadline",
            "{\"dueAt\":\"{{cf620_later_due}}\",\"version\":{{assignment_version}}}",
            "Authorization",
            "Bearer {{student_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
  }

  private void case376() throws Exception {
    var response =
        request(
            "PATCH",
            "/api/lessons/{{lesson_id}}/assignments/{{assignment_id}}/deadline",
            "{\"dueAt\":\"{{cf620_later_due}}\"}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case377() throws Exception {
    var response =
        request(
            "PATCH",
            "/api/lessons/{{lesson_id}}/assignments/{{assignment_id}}/deadline",
            "{\"dueAt\":\"{{cf620_earlier_due}}\",\"version\":{{assignment_version}}}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case378() throws Exception {
    var response =
        request(
            "PATCH",
            "/api/lessons/{{lesson_id}}/assignments/{{assignment_id}}/deadline",
            "{\"dueAt\":\"{{cf620_past_due}}\",\"version\":{{assignment_version}}}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
  }

  private void case379() throws Exception {
    set("cf620_old_version", get("assignment_version"));
    var response =
        request(
            "PATCH",
            "/api/lessons/{{lesson_id}}/assignments/{{assignment_id}}/deadline",
            "{\"dueAt\":\"{{cf620_later_due}}\",\"version\":{{assignment_version}}}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(instant(field(response.body(), "dueAt")), instant(get("cf620_later_due"))),
        "Date.parse(response.body.dueAt) === Date.parse(client.global.get(\"cf620_later_due\"))");
    verify(
        eq(field(response.body(), "title"), "Java homework 1 updated"),
        "response.body.title === \"Java homework 1 updated\"");
    verify(
        eq(field(response.body(), "description"), "Explain JVM, JDK and JRE"),
        "response.body.description === \"Explain JVM, JDK and JRE\"");
    verify(eq(field(response.body(), "maxScore"), 100), "response.body.maxScore === 100");
    verify(
        gt(field(response.body(), "version"), number(get("cf620_old_version"))),
        "response.body.version > Number(client.global.get(\"cf620_old_version\"))");
    set("assignment_version", str(field(response.body(), "version")));
  }

  private void case380() throws Exception {
    var response =
        request(
            "PATCH",
            "/api/lessons/{{lesson_id}}/assignments/{{assignment_id}}/deadline",
            "{\"dueAt\":\"{{cf620_admin_due}}\",\"version\":{{cf620_old_version}}}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
  }

  private void case381() throws Exception {
    var response =
        request(
            "PATCH",
            "/api/lessons/{{lesson_id}}/assignments/{{assignment_id}}/deadline",
            "{\"dueAt\":\"{{cf620_admin_due}}\",\"version\":{{assignment_version}}}",
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(instant(field(response.body(), "dueAt")), instant(get("cf620_admin_due"))),
        "Date.parse(response.body.dueAt) === Date.parse(client.global.get(\"cf620_admin_due\"))");
    verify(
        gt(field(response.body(), "version"), number(get("assignment_version"))),
        "response.body.version > Number(client.global.get(\"assignment_version\"))");
    set("assignment_version", str(field(response.body(), "version")));
  }

  private void case382() throws Exception {
    var response =
        request(
            "GET",
            "/api/assignments/{{assignment_id}}/submissions/me",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(str(field(response.body(), "id")), get("submission_id")),
        "String(response.body.id) === client.global.get(\"submission_id\")");
    verify(eq(field(response.body(), "status"), "GRADED"), "response.body.status === \"GRADED\"");
    verify(eq(field(response.body(), "score"), 90), "response.body.score === 90");
    verify(
        eq(field(response.body(), "feedback"), "Good explanation"),
        "response.body.feedback === \"Good explanation\"");
    verify(
        eq(str(field(response.body(), "version")), get("submission_version")),
        "String(response.body.version) === client.global.get(\"submission_version\")");
  }

  private void case383() throws Exception {
    set("cf623_previous_grade_version", get("submission_version"));
    var response =
        request(
            "PUT",
            "/api/assignments/{{assignment_id}}/submissions/{{submission_id}}/grade",
            "{\"version\":{{submission_version}},\"score\":95,\"feedback\":\"Revised evaluation\"}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "status"), "GRADED"), "response.body.status === \"GRADED\"");
    verify(eq(field(response.body(), "score"), 95), "response.body.score === 95");
    verify(
        eq(field(response.body(), "feedback"), "Revised evaluation"),
        "response.body.feedback === \"Revised evaluation\"");
    verify(
        gt(field(response.body(), "version"), number(get("cf623_previous_grade_version"))),
        "response.body.version > Number(client.global.get(\"cf623_previous_grade_version\"))");
    set("submission_version", str(field(response.body(), "version")));
  }

  private void case384() throws Exception {
    var response =
        request(
            "PUT",
            "/api/assignments/{{assignment_id}}/submissions/{{submission_id}}/grade",
            "{\"version\":{{cf623_previous_grade_version}},\"score\":20,\"feedback\":\"Stale evaluation\"}",
            "Authorization",
            "Bearer {{teacher_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 409), plus("Expected 409, got ", response.status()));
    verify(
        eq(field(response.body(), "code"), "CONCURRENT_CONFLICT"),
        "response.body.code === \"CONCURRENT_CONFLICT\"");
  }

  private void case385() throws Exception {
    var response =
        request(
            "PUT",
            "/api/assignments/{{assignment_id}}/submissions/{{submission_id}}/grade",
            "{\"version\":{{submission_version}},\"score\":100,\"feedback\":\"Unauthorized evaluation\"}",
            "Authorization",
            "Bearer {{student_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 403), plus("Expected 403, got ", response.status()));
    verify(
        eq(field(response.body(), "code"), "ACCESS_DENIED"),
        "response.body.code === \"ACCESS_DENIED\"");
  }

  private void case386() throws Exception {
    var response =
        request(
            "GET",
            "/api/assignments/{{assignment_id}}/submissions/me",
            null,
            "Authorization",
            "Bearer {{student_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "score"), 95), "response.body.score === 95");
    verify(
        eq(field(response.body(), "feedback"), "Revised evaluation"),
        "response.body.feedback === \"Revised evaluation\"");
    verify(
        eq(str(field(response.body(), "version")), get("submission_version")),
        "String(response.body.version) === client.global.get(\"submission_version\")");
  }

  private void case387() throws Exception {
    var response =
        request(
            "PUT",
            "/api/assignments/{{assignment_id}}/submissions/{{submission_id}}/grade",
            "{\"version\":{{submission_version}},\"score\":90,\"feedback\":\"Good explanation\"}",
            "Authorization",
            "Bearer {{admin_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(eq(field(response.body(), "score"), 90), "response.body.score === 90");
    verify(eq(field(response.body(), "status"), "GRADED"), "response.body.status === \"GRADED\"");
    verify(
        gt(field(response.body(), "version"), number(get("submission_version"))),
        "response.body.version > Number(client.global.get(\"submission_version\"))");
    set("submission_version", str(field(response.body(), "version")));
  }

  private void case388() throws Exception {
    each(array("cf623_auth_id", "cf623_auth_token"), k -> clear(k));
    set("cf623_auth_email", plus(plus("reg.auth623.", get("run_id")), "@courseflow.local"));
    var response =
        request(
            "POST",
            "/api/auth/register",
            "{\"displayName\":\"Authorization regression\",\"email\":\"{{cf623_auth_email}}\",\"password\":\"{{test_password}}\"}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 201), plus("Expected 201, got ", response.status()));
    set("cf623_auth_id", str(field(response.body(), "id")));
  }

  private void case389() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/login",
            "{\"email\":\"{{cf623_auth_email}}\",\"password\":\"{{test_password}}\"}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    set("cf623_auth_token", str(field(response.body(), "accessToken")));
  }

  private void case390() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/change-password",
            "{\"currentPassword\":\"IncorrectPassword2026!\",\"newPassword\":\"{{new_password}}\"}",
            "Authorization",
            "Bearer {{cf623_auth_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 400), plus("Expected 400, got ", response.status()));
    verify(
        eq(field(response.body(), "code"), "BAD_REQUEST"),
        "response.body.code === \"BAD_REQUEST\"");
  }

  private void case391() throws Exception {
    var response =
        request(
            "POST",
            "/api/users/{{cf623_auth_id}}/deactivate",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 200), plus("Expected 200, got ", response.status()));
    verify(
        eq(field(response.body(), "status"), "INACTIVE"), "response.body.status === \"INACTIVE\"");
  }

  private void case392() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/change-password",
            "{\"currentPassword\":\"{{test_password}}\",\"newPassword\":\"{{new_password}}\"}",
            "Authorization",
            "Bearer {{cf623_auth_token}}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 401), plus("Expected 401, got ", response.status()));
    verify(
        eq(field(response.body(), "code"), "AUTHENTICATION_REQUIRED"),
        "response.body.code === \"AUTHENTICATION_REQUIRED\"");
    verify(
        eq(response.header("WWW-Authenticate"), "Bearer"),
        "response.headers.valueOf(\"WWW-Authenticate\") === \"Bearer\"");
  }

  private void case393() throws Exception {
    var response =
        request(
            "POST",
            "/api/auth/login",
            "{\"email\":\"{{cf623_auth_email}}\",\"password\":\"{{test_password}}\"}",
            "Content-Type",
            "application/json");
    verify(eq(response.status(), 401), plus("Expected 401, got ", response.status()));
    verify(
        eq(field(response.body(), "code"), "INVALID_CREDENTIALS"),
        "response.body.code === \"INVALID_CREDENTIALS\"");
  }

  private void case394() throws Exception {
    var response =
        request(
            "DELETE",
            "/api/users/{{cf623_auth_id}}",
            null,
            "Authorization",
            "Bearer {{admin_token}}");
    verify(eq(response.status(), 204), plus("Expected 204, got ", response.status()));
  }
}
