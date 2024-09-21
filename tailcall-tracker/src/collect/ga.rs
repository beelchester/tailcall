use reqwest::header::{HeaderName, HeaderValue};

use super::super::Result;
use super::collectors::EventCollector;
use crate::Event;
use serde::{Deserialize, Serialize};

const GA_TRACKER_URL: &str = "https://www.google-analytics.com";
const GA_TRACKER_API_SECRET: &str = match option_env!("GA_API_SECRET") {
    Some(val) => val,
    None => "dev",
};
const GA_TRACKER_MEASUREMENT_ID: &str = match option_env!("GA_MEASUREMENT_ID") {
    Some(val) => val,
    None => "dev",
};

/// Event structure to be sent to GA
#[derive(Debug, Serialize, Deserialize)]
struct GaEvent {
    client_id: String,
    events: Vec<Event>,
}

impl GaEvent {
    pub fn new(event: Event) -> Self {
        Self { client_id: event.clone().client_id, events: vec![event] }
    }
}

pub struct GaTracker {
    base_url: String,
    api_secret: String,
    measurement_id: String,
}

impl GaTracker {
    pub fn default() -> Self {
        Self {
            base_url: GA_TRACKER_URL.to_string(),
            api_secret: GA_TRACKER_API_SECRET.to_string(),
            measurement_id: GA_TRACKER_MEASUREMENT_ID.to_string(),
        }
    }
    fn create_request(&self, event: Event) -> Result<reqwest::Request> {
        let event = GaEvent::new(event);
        tracing::debug!("Sending event: {:?}", event);
        let mut url = reqwest::Url::parse(self.base_url.as_str())?;
        url.set_path("/mp/collect");
        url.query_pairs_mut()
            .append_pair("api_secret", self.api_secret.as_str())
            .append_pair("measurement_id", self.measurement_id.as_str());
        let mut request = reqwest::Request::new(reqwest::Method::POST, url);
        let header_name = HeaderName::from_static("content-type");
        let header_value = HeaderValue::from_str("application/json")?;
        request.headers_mut().insert(header_name, header_value);

        let _ = request
            .body_mut()
            .insert(reqwest::Body::from(serde_json::to_string(&event)?));

        Ok(request)
    }
}

#[async_trait::async_trait]
impl EventCollector for GaTracker {
    async fn dispatch(&self, event: Event) -> Result<()> {
        let request = self.create_request(event)?;
        let client = reqwest::Client::new();
        let response = client.execute(request).await?;
        let status = response.status();
        let text = response.text().await?;
        tracing::debug!("Collector: {}, message: {:?}", status.as_str(), text);
        Ok(())
    }
}
