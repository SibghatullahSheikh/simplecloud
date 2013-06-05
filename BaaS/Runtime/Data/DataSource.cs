// DataSource.cs
//

using System;
using System.Collections.Generic;
using System.Threading;

namespace SimpleCloud.Data {

    public abstract class DataSource {

        private Application _app;
        private string _name;
        private Dictionary<string, object> _configuration;

        public DataSource(Application app, string name, Dictionary<string, object> configuration) {
            _app = app;
            _name = name;
            _configuration = configuration;
        }

        public Application Application {
            get {
                return _app;
            }
        }

        protected Dictionary<string, object> Configuration {
            get {
                return _configuration;
            }
        }

        protected string Name {
            get {
                return _name;
            }
        }

        public Task<object> Execute(DataRequest request, Dictionary<string, object> options) {
            if (request.Operation == DataOperation.Execute) {
                return ExecuteCustom(request, options);
            }
            else if ((request.Operation == DataOperation.Lookup) || (request.Operation == DataOperation.Query)) {
                return ExecuteQuery(request, options);
            }
            else {
                return ExecuteNonQuery(request, options);
            }
        }

        protected virtual Task<object> ExecuteCustom(DataRequest request, Dictionary<string, object> options) {
            object result = (options != null) ? Script.Or(options["result"], options) : null;
            return Deferred.Create<object>(result).Task;
        }

        protected virtual Task<object> ExecuteNonQuery(DataRequest request, Dictionary<string, object> options) {
            return Deferred.Create<object>(Script.Undefined).Task;
        }

        protected abstract Task<object> ExecuteQuery(DataRequest request, Dictionary<string, object> options);

        public virtual object GetService(Dictionary<string, object> options) {
            return null;
        }
    }
}
