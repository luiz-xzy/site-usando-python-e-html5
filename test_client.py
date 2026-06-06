import unittest

from pyton_social.client import LyricsClient


class LyricsClientTest(unittest.TestCase):
    def setUp(self) -> None:
        self.client = LyricsClient()

    def test_clean_text_removes_features(self) -> None:
        result = self.client._clean_text("Artista feat. Convidado")
        self.assertEqual(result, "Artista")

    def test_clean_text_removes_parenthesis(self) -> None:
        result = self.client._clean_text("Título da Música (Ao Vivo)")
        self.assertEqual(result, "Título da Música")

    def test_search_returns_empty_for_blank_query(self) -> None:
        self.assertEqual(self.client.search(""), [])

    def test_get_lyrics_returns_none_for_missing_info(self) -> None:
        self.assertIsNone(self.client.get_lyrics("", ""))


if __name__ == "__main__":
    unittest.main()
