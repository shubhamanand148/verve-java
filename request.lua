local counter = 1
request = function()
    local url = "/api/verve/accept?id=" .. counter
    counter = counter + 1
    return wrk.format("GET", url)
end
