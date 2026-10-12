package dev.leilaalgarve.jogoacoes.competition.steps;

import dev.leilaalgarve.jogoacoes.common.testsupport.CompetitionFixtures;
import dev.leilaalgarve.jogoacoes.common.testsupport.ScenarioWorld;
import dev.leilaalgarve.jogoacoes.common.testsupport.UserMother;
import dev.leilaalgarve.jogoacoes.competition.CompetitionStatus;
import dev.leilaalgarve.jogoacoes.competition.CompetitionType;
import dev.leilaalgarve.jogoacoes.competition.Participation;
import dev.leilaalgarve.jogoacoes.competition.ParticipationRepository;
import dev.leilaalgarve.jogoacoes.competition.ParticipationStatus;
import dev.leilaalgarve.jogoacoes.competition.RequestType;
import dev.leilaalgarve.jogoacoes.user.User;
import dev.leilaalgarve.jogoacoes.user.UserRepository;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-039 (T001): written against `POST /players/search` before it exists in
 * docs/openapi.yaml, raw JSON, so this is expected to fail until the tasks after T001 land.
 *
 * <p>The database is shared across scenarios and runs, so each scenario gets its own e-mail
 * domains ("main", "other", "bulk" are aliases for unique domains) -- a fixed address like
 * ana@acme.com would clash on the next run. Filters that can't be scoped by domain (competition
 * type alone, ownership alone, no filter) also return users from other tests, so those
 * assertions page through the whole result and look only at this scenario's users. These users
 * never get an e-mail, which is why their addresses don't go through {@code TestEmails}.
 */
public class SearchPlayersSteps {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ScenarioWorld world;
    private final UserMother userMother;
    private final CompetitionFixtures competitionFixtures;
    private final ParticipationRepository participationRepository;
    private final UserRepository userRepository;

    private final Map<String, String> domainsByAlias = new HashMap<>();
    private final Map<String, User> scenarioUsersByName = new LinkedHashMap<>();
    private final List<User> bulkPlayers = new ArrayList<>();
    private Map<String, Object> lastSearch;

    public SearchPlayersSteps(ScenarioWorld world, UserMother userMother, CompetitionFixtures competitionFixtures,
                              ParticipationRepository participationRepository, UserRepository userRepository) {
        this.world = world;
        this.userMother = userMother;
        this.competitionFixtures = competitionFixtures;
        this.participationRepository = participationRepository;
        this.userRepository = userRepository;
    }

    @Given("the following players exist, in competitions created by the administrator:")
    public void the_following_players_exist(DataTable table) {
        scenarioUsersByName.put("the administrator", world.getCurrentUser());
        for (Map<String, String> row : table.asMaps()) {
            String name = row.get("name");
            String email = name.toLowerCase(Locale.ROOT) + "@" + domain(row.get("email domain"));
            User player = userMother.registeredPlayer(name, email);
            scenarioUsersByName.put(name, player);
            join(player, CompetitionType.PUBLIC, Integer.parseInt(row.get("public")));
            join(player, CompetitionType.PRIVATE, Integer.parseInt(row.get("private")));
        }
    }

    @Given("{int} additional players with the {string} e-mail domain exist")
    public void additional_players_with_the_e_mail_domain_exist(int count, String alias) {
        for (int i = 1; i <= count; i++) {
            String name = String.format("Bulk player %02d", i);
            bulkPlayers.add(userMother.registeredPlayer(name, "bulk" + i + "@" + domain(alias)));
        }
    }

    @When("they search players with no filter")
    public void they_search_players_with_no_filter() {
        search(new HashMap<>());
    }

    @When("they search players filtering by competition type {word}")
    public void they_search_players_filtering_by_competition_type(String type) {
        search(filter("competitionType", type));
    }

    @When("they search players filtering by the {string} e-mail domain")
    public void they_search_players_filtering_by_the_e_mail_domain(String alias) {
        search(filter("emailDomain", domain(alias)));
    }

    @When("they search players filtering by competition type {word} and the {string} e-mail domain")
    public void they_search_players_filtering_by_type_and_domain(String type, String alias) {
        Map<String, Object> body = filter("competitionType", type);
        body.put("emailDomain", domain(alias));
        search(body);
    }

    @When("they search players filtering by owners only")
    public void they_search_players_filtering_by_owners_only() {
        search(filter("owner", true));
    }

    @When("they search players filtering by non-owners only")
    public void they_search_players_filtering_by_non_owners_only() {
        search(filter("owner", false));
    }

    @When("they search players filtering by the {string} e-mail domain with page size {int}")
    public void they_search_players_filtering_by_domain_with_page_size(String alias, int size) {
        Map<String, Object> body = filter("emailDomain", domain(alias));
        body.put("size", size);
        search(body);
    }

    @When("they search players filtering by the {string} e-mail domain on page {int}")
    public void they_search_players_filtering_by_domain_on_page(String alias, int page) {
        Map<String, Object> body = filter("emailDomain", domain(alias));
        body.put("page", page);
        search(body);
    }

    @When("they search players with page size {int}")
    public void they_search_players_with_page_size(int size) {
        search(filter("size", size));
    }

    @When("they search players with page {int}")
    public void they_search_players_with_page(int page) {
        search(filter("page", page));
    }

    @When("they search players with e-mail domain {string}")
    public void they_search_players_with_e_mail_domain(String emailDomain) {
        search(filter("emailDomain", emailDomain));
    }

    @When("they search players with competition type {string}")
    public void they_search_players_with_competition_type(String type) {
        search(filter("competitionType", type));
    }

    @When("they try to search players")
    public void they_try_to_search_players() {
        search(new HashMap<>());
    }

    @Then("the result total is the number of users in the system")
    public void the_result_total_is_the_number_of_users_in_the_system() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        assertThat(world.getLastResponse().jsonPath().getLong("total")).isEqualTo(userRepository.count());
    }

    @Then("the result has the default page size of players, or fewer if there are fewer users")
    public void the_result_has_the_default_page_size_of_players() {
        long expected = Math.min(DEFAULT_PAGE_SIZE, world.getLastResponse().jsonPath().getLong("total"));
        assertThat(world.getLastResponse().jsonPath().getList("items")).hasSize((int) expected);
        assertThat(world.getLastResponse().jsonPath().getInt("size")).isEqualTo(DEFAULT_PAGE_SIZE);
    }

    @Then("the result contains {string}, {string}, {string}, {string} and the administrator")
    public void the_result_contains_and_the_administrator(String a, String b, String c, String d) {
        Set<Long> found = allMatchingUserIds();
        for (String name : List.of(a, b, c, d, "the administrator")) {
            assertThat(found).as(name).contains(scenarioUsersByName.get(name).getId());
        }
    }

    @Then("among this scenario's users, the result contains exactly {}")
    public void among_this_scenario_s_users_the_result_contains_exactly(String names) {
        Set<Long> found = allMatchingUserIds();
        Set<String> foundScenarioNames = new LinkedHashSet<>();
        scenarioUsersByName.forEach((name, user) -> {
            if (found.contains(user.getId())) {
                foundScenarioNames.add(name);
            }
        });
        assertThat(foundScenarioNames).containsExactlyInAnyOrderElementsOf(parseNames(names));
    }

    @Then("the result contains exactly {string}")
    public void the_result_contains_exactly(String name) {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        assertThat(world.getLastResponse().jsonPath().getLong("total")).isEqualTo(1);
        assertThat(world.getLastResponse().jsonPath().getLong("items[0].userId"))
                .isEqualTo(scenarioUsersByName.get(name).getId());
    }

    @Then("the result total is {int}")
    public void the_result_total_is(int total) {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        assertThat(world.getLastResponse().jsonPath().getLong("total")).isEqualTo(total);
    }

    @Then("the result has {int} players")
    public void the_result_has_players(int count) {
        assertThat(world.getLastResponse().jsonPath().getList("items")).hasSize(count);
    }

    @Then("the result has no players")
    public void the_result_has_no_players() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        assertThat(world.getLastResponse().jsonPath().getList("items")).isEmpty();
    }

    @Then("requesting pages 0, 1 and 2 returns every {string} player exactly once, ordered by name")
    public void requesting_pages_returns_every_player_exactly_once_ordered_by_name(String alias) {
        List<String> names = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        for (int page = 0; page <= 2; page++) {
            Map<String, Object> body = filter("emailDomain", domain(alias));
            body.put("size", 10);
            body.put("page", page);
            Response response = post(body);
            assertThat(response.statusCode()).isEqualTo(200);
            names.addAll(response.jsonPath().getList("items.name", String.class));
            ids.addAll(response.jsonPath().getList("items.userId", Long.class));
        }
        assertThat(ids).doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(bulkPlayers.stream().map(User::getId).toList());
        assertThat(names).isSorted();
    }

    private Set<Long> allMatchingUserIds() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        long total = world.getLastResponse().jsonPath().getLong("total");
        Set<Long> ids = new LinkedHashSet<>();
        for (int page = 0; ids.size() < total; page++) {
            Map<String, Object> body = new HashMap<>(lastSearch);
            body.put("page", page);
            body.put("size", MAX_PAGE_SIZE);
            Response response = post(body);
            assertThat(response.statusCode()).isEqualTo(200);
            List<Long> pageIds = response.jsonPath().getList("items.userId", Long.class);
            if (pageIds.isEmpty()) {
                break;
            }
            ids.addAll(pageIds);
        }
        return ids;
    }

    private void search(Map<String, Object> body) {
        lastSearch = body;
        world.setLastResponse(post(body));
    }

    private Response post(Map<String, Object> body) {
        return world.request().body(body).when().post("/players/search");
    }

    private Map<String, Object> filter(String key, Object value) {
        Map<String, Object> body = new HashMap<>();
        body.put(key, value);
        return body;
    }

    /** "MAIN" is the "main" domain in upper case -- the domain filter must not care about case. */
    private String domain(String alias) {
        String domain = domainsByAlias.computeIfAbsent(alias.toLowerCase(Locale.ROOT),
                a -> a + "-" + UUID.randomUUID().toString().substring(0, 8) + ".test");
        return alias.equals(alias.toUpperCase(Locale.ROOT)) ? domain.toUpperCase(Locale.ROOT) : domain;
    }

    private void join(User player, CompetitionType type, int count) {
        CompetitionStatus status = type == CompetitionType.PUBLIC ? CompetitionStatus.OPEN : CompetitionStatus.AWAITING_INVITES;
        for (int i = 0; i < count; i++) {
            Participation participation = new Participation();
            participation.setCompetition(competitionFixtures.createdBy(world.getCurrentUser(), type, status));
            participation.setUser(player);
            participation.setEmail(player.getEmail());
            participation.setStatus(ParticipationStatus.IN_COMPETITION);
            participation.setRequestType(type == CompetitionType.PUBLIC ? RequestType.REQUEST : RequestType.INVITE);
            participation.setJoinedAt(LocalDate.now());
            participationRepository.save(participation);
        }
    }

    private static List<String> parseNames(String names) {
        return Arrays.stream(names.split(",|\\band\\b"))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(name -> name.replace("\"", ""))
                .toList();
    }
}
