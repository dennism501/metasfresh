import { connectRouter } from 'connected-react-router';

import appHandler from './appHandler';
import listHandler from './listHandler';
import menuHandler from './menuHandler';
import windowHandler from './windowHandler';
import pluginsHandler from './pluginsHandler';
import viewHandler from './viewHandler';
import filters from './filterHandler';
import table from './tableHandler';
import commentsPanel from './commentsPanel';
import tables from './tables';

export const createRootReducer = (history) => ({
  router: connectRouter(history),
  appHandler,
  listHandler,
  menuHandler,
  windowHandler,
  viewHandler,
  pluginsHandler,
  filters,
  table,
  commentsPanel,
  tables,
});
