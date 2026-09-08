import unittest

from config.settings import Settings


class SettingsTest(unittest.TestCase):
    def test_java_url_uses_normalized_base_url(self):
        settings = Settings(
            deepseek_api_key="test-key",
            java_base_url="http://java.example/",
        )

        self.assertEqual(
            settings.java_url("/internal/agent/cart/4"),
            "http://java.example/internal/agent/cart/4",
        )

    def test_rejects_blank_deepseek_api_key(self):
        with self.assertRaises(ValueError):
            Settings(deepseek_api_key=" ")

    def test_rejects_invalid_postgres_pool_size(self):
        with self.assertRaises(ValueError):
            Settings(
                deepseek_api_key="test-key",
                postgres_pool_min_size=11,
                postgres_pool_max_size=10,
            )


if __name__ == "__main__":
    unittest.main()
