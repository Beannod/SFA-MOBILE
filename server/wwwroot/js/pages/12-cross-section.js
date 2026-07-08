    // Override the stubs from shared nav with real implementations
    window.goToOrdersForCustomer = function(customerId, customerName) {
        showSection('orders');
        setTimeout(function() { if (typeof ordersFilterByCustomer==='function') ordersFilterByCustomer(customerId, customerName); }, 50);
    };
    window.goToCreateOrderForCustomer = function(customerId, customerName) {
        showSection('orders');
        setTimeout(function() { if (typeof ordersOpenCreateForCustomer==='function') ordersOpenCreateForCustomer(customerId, customerName); }, 50);
    };
    window.goToCustomersForUser = function(userId, userName) {
        showSection('customers');
        setTimeout(function() { if (typeof custFilterByUser==='function') custFilterByUser(userId, userName); }, 50);
    };

