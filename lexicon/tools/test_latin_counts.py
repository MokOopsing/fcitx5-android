"""latin_counts.py counts a word's uses in each spelling, but a page it is on once whatever its
spellings there: latin.py takes that as the word's pages."""
import importlib.util
import sys
import types
import unittest

# only count reads the shards with pyarrow, which CI does not have
if importlib.util.find_spec('pyarrow') is None:
    pyarrow = types.ModuleType('pyarrow')
    pyarrow.parquet = types.ModuleType('pyarrow.parquet')
    sys.modules.update({'pyarrow': pyarrow, 'pyarrow.parquet': pyarrow.parquet})

import latin_counts  # noqa: E402


class LatinCountsTest(unittest.TestCase):

    def test_a_page_counts_once_for_a_word_in_any_spelling(self):
        # APP and app on one page, app again on another; no Han beside the last page's app
        words, pages = latin_counts.tally(['用APP下载，app也行', 'app很好', 'I use app daily'])
        self.assertEqual({'APP': 1, 'app': 2}, dict(words))
        self.assertEqual({'app': 2}, dict(pages))
