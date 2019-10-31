package consumer;

import static org.assertj.core.api.Assertions.assertThat;

import au.com.dius.pact.consumer.Pact;
import au.com.dius.pact.consumer.PactProviderRuleMk2;
import au.com.dius.pact.consumer.PactVerification;
import au.com.dius.pact.consumer.dsl.DslPart;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.model.RequestResponsePact;
import io.pactfoundation.consumer.dsl.LambdaDsl;
import java.sql.Date;
import java.time.ZoneId;
import org.assertj.core.groups.Tuple;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.HttpClientErrorException;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "provider.base-url:http://localhost:${RANDOM_PORT}",
    classes = LocationServiceClient.class)
@Ignore
public class LocationServiceContractTest {

    private static final String ZIPCODE = "90210";
    private static final String COUNTRY = "United States";
    private static final String COUNTRY_ABBREVIATION = "US";

    @ClassRule
    public static RandomPortRule randomPort = new RandomPortRule();

    @Rule
    public PactProviderRuleMk2 provider = new PactProviderRuleMk2("provider", null,
        randomPort.getPort(), this);

    @Rule
    public ExpectedException expandException = ExpectedException.none();

    @Autowired
    private LocationServiceClient locationServiceClient;


    @Pact(consumer = "consumer")
    public RequestResponsePact pactLocationExists(PactDslWithProvider builder) {

        // See https://github.com/DiUS/pact-jvm/tree/master/consumer/pact-jvm-consumer-junit
        DslPart body = LambdaDsl.newJsonBody((o) -> o
                .stringType("zipCode", ZIPCODE)
                .stringType("country", COUNTRY)
                .stringType("countryAbbreviation", COUNTRY_ABBREVIATION)
                .minArrayLike("places", 1, 1, place -> place
                        .stringType("placeName", "Beverly Hills")
                        .stringType("state", "California")
                        .stringType("stateAbbreviation", "CA")
                )).build();

        return builder.given(
            "Location exists for US 90210")
            .uponReceiving("A request to /us/90210")
            .path("/us/90210")
            .method("GET")
            .willRespondWith()
            .status(201)
            .body(body)
            .toPact();
    }

    @Pact(consumer = "consumer")
    public RequestResponsePact pactLocationDoesNotExist(PactDslWithProvider builder) {

        return builder.given(
            "Location does not exist for US 99999")
            .uponReceiving("A request to /us/99999")
                .path("/us/99999")
                .method("GET")
                .willRespondWith()
                .status(404)
                .toPact();
    }

    @PactVerification(fragment = "pactLocationExists")
    @Test
    public void locationExists() {
        final Location location = locationServiceClient.getLocation("us", "90210");

        assertThat(location.getZipCode()).isEqualTo(ZIPCODE);
        assertThat(location.getCountry()).isEqualTo(COUNTRY);
        assertThat(location.getCountryAbbreviation()).isEqualTo(COUNTRY_ABBREVIATION);
        assertThat(location.getPlaces()).hasSize(1)
            .extracting(Place::getPlaceName, Place::getState, Place::getStateAbbreviation)
            .containsExactly(Tuple.tuple("Beverly Hills", "California", "CA"));
    }

    @PactVerification(fragment = "pactLocationDoesNotExist")
    @Test
    public void locationDoesNotExist() {
        expandException.expect(HttpClientErrorException.class);
        expandException.expectMessage("404 Not Found");

        locationServiceClient.getLocation("us", "99999");
    }
}